package com.adlin.orin.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;

/**
 * 缓存监控指标配置
 * 为缓存系统添加 Prometheus 监控指标
 */
@Slf4j
@Configuration
@EnableScheduling
public class CacheMetricsConfig {

    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;

    public CacheMetricsConfig(CacheManager cacheManager, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 应用启动后记录可用的缓存
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStarted() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        log.info("=== Available Caches ===");
        for (String cacheName : cacheNames) {
            log.info("  - {}", cacheName);
        }
        log.info("Cache metrics will be available at /actuator/metrics/cache.*");
        log.info("========================");
    }

    /**
     * 定期打印缓存统计信息（开发环境调试用）
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 启动1分钟后开始，每5分钟
    public void logCacheStatistics() {
        if (!(cacheManager instanceof RedisCacheManager)) {
            return;
        }

        Collection<String> cacheNames = cacheManager.getCacheNames();
        log.info("=== Cache Statistics ===");

        for (String cacheName : cacheNames) {
            try {
                // 获取指标
                Counter hitCounter = meterRegistry.find("cache.gets")
                        .tag("cache", cacheName)
                        .tag("result", "hit")
                        .counter();

                Counter missCounter = meterRegistry.find("cache.gets")
                        .tag("cache", cacheName)
                        .tag("result", "miss")
                        .counter();

                double hitRate = hitCounter != null ? hitCounter.count() : 0.0;
                double missRate = missCounter != null ? missCounter.count() : 0.0;
                double total = hitRate + missRate;
                double hitRatio = total > 0 ? (hitRate / total) * 100 : 0;

                if (total > 0) {
                    log.info("Cache [{}] - Hits: {}, Misses: {}, Hit Rate: {:.2f}%",
                            cacheName, (int) hitRate, (int) missRate, hitRatio);
                }
            } catch (Exception e) {
                log.debug("Failed to get statistics for cache: {}", cacheName, e);
            }
        }
        log.info("========================");
    }

    /**
     * 缓存健康检查 - 检测命中率过低的情况
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 120000) // 启动2分钟后开始，每10分钟
    public void checkCacheHealth() {
        Collection<String> cacheNames = cacheManager.getCacheNames();

        for (String cacheName : cacheNames) {
            try {
                Counter hitCounter = meterRegistry.find("cache.gets")
                        .tag("cache", cacheName)
                        .tag("result", "hit")
                        .counter();

                Counter missCounter = meterRegistry.find("cache.gets")
                        .tag("cache", cacheName)
                        .tag("result", "miss")
                        .counter();

                double hitCount = hitCounter != null ? hitCounter.count() : 0.0;
                double missCount = missCounter != null ? missCounter.count() : 0.0;
                double total = hitCount + missCount;

                // 如果总数超过100次且命中率低于30%，发出警告
                if (total > 100) {
                    double hitRatio = (hitCount / total) * 100;
                    if (hitRatio < 30.0) {
                        log.warn("⚠️ Cache [{}] has low hit rate: {:.2f}% (Hits: {}, Misses: {})",
                                cacheName, hitRatio, (int) hitCount, (int) missCount);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to check health for cache: {}", cacheName, e);
            }
        }
    }
}
