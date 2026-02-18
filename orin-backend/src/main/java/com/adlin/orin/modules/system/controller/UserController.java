package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "用户管理接口")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/profile/{username}")
    public ResponseEntity<?> getProfile(@PathVariable String username) {
        return userService.getUserByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody SysUser user) {
        // Simple update for now, in production you'd want to validate and only update
        // allowed fields
        SysUser updated = userService.updateUser(user);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "更新用户头像")
    @PostMapping("/avatar")
    public ResponseEntity<?> updateAvatar(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        String avatarUrl = payload.get("avatarUrl").toString();

        userService.updateAvatar(userId, avatarUrl);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("avatarUrl", avatarUrl);
        return ResponseEntity.ok(response);
    }
}
