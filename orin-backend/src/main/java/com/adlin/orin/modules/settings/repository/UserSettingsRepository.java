package com.adlin.orin.modules.settings.repository;

import com.adlin.orin.modules.settings.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /**
     * 获取用户的所有设置
     */
    List<UserSettings> findByUserId(String userId);

    /**
     * 获取用户的指定类型设置
     */
    List<UserSettings> findByUserIdAndSettingType(String userId, String settingType);

    /**
     * 获取用户的指定设置
     */
    Optional<UserSettings> findByUserIdAndSettingKey(String userId, String settingKey);

    /**
     * 删除用户的所有设置
     */
    @Modifying
    @Query("DELETE FROM UserSettings s WHERE s.userId = :userId")
    void deleteByUserId(String userId);

    /**
     * 删除用户的指定类型设置
     */
    @Modifying
    @Query("DELETE FROM UserSettings s WHERE s.userId = :userId AND s.settingType = :settingType")
    void deleteByUserIdAndSettingType(String userId, String settingType);
}
