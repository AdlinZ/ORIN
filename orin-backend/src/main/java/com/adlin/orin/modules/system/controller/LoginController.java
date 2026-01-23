package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.dto.LoginDTO;
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
@CrossOrigin(origins = "*")
public class LoginController {

    @Autowired
    private AuthService authService;

    @Autowired
    private com.adlin.orin.security.JwtService jwtService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO) {
        Map<String, Object> authResult = authService.login(loginDTO.getUsername(), loginDTO.getPassword());

        if (authResult != null) {
            com.adlin.orin.modules.system.entity.SysUser user = (com.adlin.orin.modules.system.entity.SysUser) authResult
                    .get("user");
            java.util.List<String> roles = (java.util.List<String>) authResult.get("roles");

            Map<String, Object> extraClaims = new HashMap<>();
            extraClaims.put("roles", roles);

            String token = jwtService.generateToken(String.valueOf(user.getUserId()), user.getUsername(), extraClaims);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", user);
            response.put("roles", roles); // 包含用户角色
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("message", "用户名或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
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
