package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.dto.LoginDTO;
import com.adlin.orin.modules.system.dto.RegisterDTO;
import com.adlin.orin.modules.system.dto.UserResponseDTO;
import com.adlin.orin.modules.system.service.dto.AuthResult;
import com.adlin.orin.modules.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "身份验证与鉴权")
public class LoginController {

    @Autowired
    private AuthService authService;

    @Autowired
    private com.adlin.orin.security.JwtService jwtService;

    @Autowired
    private com.adlin.orin.modules.audit.service.AuditLogService auditLogService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO, jakarta.servlet.http.HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        AuthResult authResult = null;

        try {
            authResult = authService.login(loginDTO.getUsername(), loginDTO.getPassword());
        } catch (Exception e) {
            auditLogService.logApiCall(
                    loginDTO.getUsername(), null, "SYSTEM", "AUTH",
                    "/api/v1/auth/login", "POST", null, ipAddress, userAgent,
                    "username=" + loginDTO.getUsername(), "Login Failed: " + e.getMessage(),
                    401, 0L, 0, 0, 0.0, false, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("message", "登录失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        if (authResult != null) {
            com.adlin.orin.modules.system.entity.SysUser user = authResult.user();
            java.util.List<String> roles = authResult.roles();

            String token = buildToken(user, roles, loginDTO.isRememberMe());

            // Log Success
            auditLogService.logApiCall(
                    String.valueOf(user.getUserId()), null, "SYSTEM", "AUTH",
                    "/api/v1/auth/login", "POST", "UserLogin", ipAddress, userAgent,
                    "username=" + loginDTO.getUsername(), "Login Success",
                    200, 0L, 0, 0, 0.0, true, null);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", UserResponseDTO.fromEntity(user));
            response.put("roles", roles); // 包含用户角色
            return ResponseEntity.ok(response);
        } else {
            // Log Fail (Invalid credentials)
            auditLogService.logApiCall(
                    loginDTO.getUsername(), null, "SYSTEM", "AUTH",
                    "/api/v1/auth/login", "POST", null, ipAddress, userAgent,
                    "username=" + loginDTO.getUsername(), "Invalid Credentials",
                    401, 0L, 0, 0, 0.0, false, "Invalid Credentials");

            Map<String, String> error = new HashMap<>();
            error.put("message", "用户名或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @Operation(summary = "用户自助注册", description = "创建个人账号，默认授予 ROLE_USER 并返回可直接进入 /chat 的 JWT")
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO registerDTO, jakarta.servlet.http.HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        AuthResult authResult;

        try {
            authResult = authService.register(registerDTO);
        } catch (RuntimeException e) {
            String username = registerDTO != null ? registerDTO.getUsername() : null;
            auditLogService.logApiCall(
                    username, null, "SYSTEM", "AUTH",
                    "/api/v1/auth/register", "POST", null, ipAddress, userAgent,
                    "username=" + username,
                    "Register Failed: " + e.getMessage(),
                    400, 0L, 0, 0, 0.0, false, e.getMessage());
            throw e;
        }

        com.adlin.orin.modules.system.entity.SysUser user = authResult.user();
        java.util.List<String> roles = authResult.roles();
        String token = buildToken(user, roles, registerDTO != null && registerDTO.isRememberMe());

        auditLogService.logApiCall(
                String.valueOf(user.getUserId()), null, "SYSTEM", "AUTH",
                "/api/v1/auth/register", "POST", "UserRegister", ipAddress, userAgent,
                "username=" + user.getUsername(), "Register Success",
                200, 0L, 0, 0, 0.0, true, null);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", UserResponseDTO.fromEntity(user));
        response.put("roles", roles);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "注册能力状态", description = "返回当前环境是否开放个人账号自助注册")
    @GetMapping("/registration-status")
    public Map<String, Object> registrationStatus() {
        return Map.of("enabled", authService.isSelfRegistrationEnabled());
    }

    private String buildToken(com.adlin.orin.modules.system.entity.SysUser user,
                              java.util.List<String> roles,
                              boolean rememberMe) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("roles", roles);
        return jwtService.generateToken(String.valueOf(user.getUserId()), user.getUsername(), extraClaims, rememberMe);
    }

    @Operation(summary = "刷新Token", description = "使用当前有效的Token获取新Token，延长登录有效期")
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
        try {
            // 验证 Authorization header
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "无效的Authorization头");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            String oldToken = authHeader.substring(7);

            // 验证Token是否有效
            if (!jwtService.validateToken(oldToken)) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Token已过期或无效");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            // 使用JwtService刷新Token
            String newToken = jwtService.refreshToken(oldToken);

            Map<String, Object> response = new HashMap<>();
            response.put("token", newToken);
            response.put("message", "Token刷新成功");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Token刷新失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @Operation(summary = "验证Token", description = "检查当前Token是否有效")
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Map<String, Object> response = new HashMap<>();
                response.put("valid", false);
                response.put("message", "无效的Authorization头");
                return ResponseEntity.ok(response);
            }

            String token = authHeader.substring(7);
            boolean isValid = jwtService.validateToken(token);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            if (isValid) {
                response.put("userId", jwtService.extractUserId(token));
                response.put("username", jwtService.extractUsername(token));
                response.put("expiration", jwtService.extractExpiration(token));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
