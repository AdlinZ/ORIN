package com.adlin.orin.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置类
 * 配置Redis缓存策略，包括序列化方式和过期时间
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(60);
    private static final Duration AGENT_TTL = Duration.ofMinutes(30);
    private static final Duration KNOWLEDGE_TTL = Duration.ofHours(2);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 默认配置
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 针对不同CacheName的特定配置
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Agent相关缓存
        cacheConfigurations.put("agents", defaultCacheConfig.entryTtl(AGENT_TTL));
        cacheConfigurations.put("agent_list", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        // Knowledge相关缓存
        cacheConfigurations.put("knowledge_bases", defaultCacheConfig.entryTtl(KNOWLEDGE_TTL));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
