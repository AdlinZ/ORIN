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
}
