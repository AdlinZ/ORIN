package com.adlin.orin.modules.settings.service;

import com.adlin.orin.modules.settings.entity.UserSettings;
import com.adlin.orin.modules.settings.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户设置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository settingsRepository;

    /**
     * 获取用户的所有设置
     */
    public Map<String, Object> getUserSettings(String userId) {
        List<UserSettings> settings = settingsRepository.findByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        
        for (UserSettings setting : settings) {
            result.put(setting.getSettingKey(), setting.getSettingValue());
        }
        
        return result;
    }

    /**
     * 获取用户的指定类型设置
     */
    public Map<String, Object> getUserSettingsByType(String userId, String settingType) {
        List<UserSettings> settings = settingsRepository.findByUserIdAndSettingType(userId, settingType);
        Map<String, Object> result = new HashMap<>();
        
        for (UserSettings setting : settings) {
            result.put(setting.getSettingKey(), setting.getSettingValue());
        }
        
        return result;
    }

    /**
     * 获取用户指定设置
     */
    public Optional<String> getUserSetting(String userId, String settingKey) {
        return settingsRepository.findByUserIdAndSettingKey(userId, settingKey)
                .map(UserSettings::getSettingValue);
    }

    /**
     * 设置用户设置
     */
    @Transactional
    public UserSettings setUserSetting(String userId, String settingKey, String settingValue) {
        Optional<UserSettings> existing = settingsRepository.findByUserIdAndSettingKey(userId, settingKey);
        
        if (existing.isPresent()) {
            UserSettings setting = existing.get();
            setting.setSettingValue(settingValue);
            return settingsRepository.save(setting);
        } else {
            // 解析设置类型（从 key 中提取）
            String settingType = extractSettingType(settingKey);
            
            UserSettings setting = UserSettings.builder()
                    .userId(userId)
                    .settingType(settingType)
                    .settingKey(settingKey)
                    .settingValue(settingValue)
                    .build();
            
            return settingsRepository.save(setting);
        }
    }

    /**
     * 批量设置用户设置
     */
    @Transactional
    public void setUserSettings(String userId, Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            setUserSetting(userId, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 删除用户设置
     */
    @Transactional
    public void deleteUserSetting(String userId, String settingKey) {
        settingsRepository.findByUserIdAndSettingKey(userId, settingKey)
                .ifPresent(settingsRepository::delete);
    }

    /**
     * 删除用户的所有设置
     */
    @Transactional
    public void deleteAllUserSettings(String userId) {
        settingsRepository.deleteByUserId(userId);
    }

    /**
     * 从设置键中提取设置类型
     */
    private String extractSettingType(String settingKey) {
        if (settingKey.contains(".")) {
            return settingKey.substring(0, settingKey.indexOf("."));
        }
        return "general";
    }

    /**
     * 获取默认设置
     */
    public Map<String, Object> getDefaultSettings() {
        return Map.of(
                "theme", "dark",
                "language", "zh-CN",
                "timezone", "Asia/Shanghai",
                "notifications.enabled", true,
                "pageSize", 20
        );
    }
}
