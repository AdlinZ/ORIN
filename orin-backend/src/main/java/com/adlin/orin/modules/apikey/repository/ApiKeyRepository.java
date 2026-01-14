package com.adlin.orin.modules.apikey.repository;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API密钥仓库
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    /**
     * 通过密钥哈希查找
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * 通过密钥前缀查找
     */
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    /**
     * 获取用户的所有密钥
     */
    List<ApiKey> findByUserId(String userId);

    /**
     * 获取用户的所有启用密钥
     */
    List<ApiKey> findByUserIdAndEnabledTrue(String userId);
}
