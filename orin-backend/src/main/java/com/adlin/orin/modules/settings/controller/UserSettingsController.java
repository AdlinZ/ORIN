package com.adlin.orin.modules.settings.controller;

import com.adlin.orin.modules.settings.entity.UserSettings;
import com.adlin.orin.modules.settings.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户设置控制器
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "User Settings", description = "用户个性化设置")
public class UserSettingsController {

    private final UserSettingsService settingsService;

    @Operation(summary = "获取用户所有设置")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserSettings(
            @RequestHeader(value = "X-User-Id") String userId) {
        return ResponseEntity.ok(settingsService.getUserSettings(userId));
    }

    @Operation(summary = "获取用户指定类型设置")
    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> getUserSettingsByType(
            @RequestHeader(value = "X-User-Id") String userId,
            @PathVariable String type) {
        return ResponseEntity.ok(settingsService.getUserSettingsByType(userId, type));
    }

    @Operation(summary = "获取用户指定设置")
    @GetMapping("/{type}/{key}")
    public ResponseEntity<?> getUserSetting(
            @RequestHeader(value = "X-User-Id") String userId,
            @PathVariable String type,
            @PathVariable String key) {
        String settingKey = type + "." + key;
        return settingsService.getUserSetting(userId, settingKey)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "设置用户设置")
    @PostMapping("/{type}/{key}")
    public ResponseEntity<UserSettings> setUserSetting(
            @RequestHeader(value = "X-User-Id") String userId,
            @PathVariable String type,
            @PathVariable String key,
            @RequestBody Map<String, String> request) {
        String settingKey = type + "." + key;
        String value = request.get("value");
        UserSettings setting = settingsService.setUserSetting(userId, settingKey, value);
        return ResponseEntity.ok(setting);
    }

    @Operation(summary = "批量设置用户设置")
    @PostMapping
    public ResponseEntity<Void> setUserSettings(
            @RequestHeader(value = "X-User-Id") String userId,
            @RequestBody Map<String, String> settings) {
        settingsService.setUserSettings(userId, settings);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "删除用户设置")
    @DeleteMapping("/{type}/{key}")
    public ResponseEntity<Void> deleteUserSetting(
            @RequestHeader(value = "X-User-Id") String userId,
            @PathVariable String type,
            @PathVariable String key) {
        String settingKey = type + "." + key;
        settingsService.deleteUserSetting(userId, settingKey);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "删除用户所有设置")
    @DeleteMapping
    public ResponseEntity<Void> deleteAllUserSettings(
            @RequestHeader(value = "X-User-Id") String userId) {
        settingsService.deleteAllUserSettings(userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取默认设置")
    @GetMapping("/defaults")
    public ResponseEntity<Map<String, Object>> getDefaultSettings() {
        return ResponseEntity.ok(settingsService.getDefaultSettings());
    }
}
