package com.adlin.orin.modules.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_PREFIX = "orin:";
    private static final long DEFAULT_TTL = 3600; // 1小时

    /**
     * 设置缓存
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + key, value, DEFAULT_TTL, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存并指定过期时间
     */
    public void set(String key, String value, long ttlSeconds) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取缓存
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(CACHE_PREFIX + key);
    }

    /**
     * 删除缓存
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(CACHE_PREFIX + key);
    }

    /**
     * 检查缓存是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(CACHE_PREFIX + key);
    }

    /**
     * 获取所有缓存键
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(CACHE_PREFIX + pattern);
    }

    /**
     * 清空所有缓存
     */
    public void flushAll() {
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 获取缓存统计
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
        stats.put("totalKeys", keys != null ? keys.size() : 0);
        
        // 尝试获取 Redis 内存信息
        try {
            Properties info = redisTemplate.getConnectionFactory()
                    .getConnection().info("memory");
            stats.put("usedMemory", info.getProperty("used_memory_human"));
        } catch (Exception e) {
            stats.put("usedMemory", "N/A");
        }
        
        return stats;
    }
}
