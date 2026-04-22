package com.adlin.orin.modules.monitor.task;

import com.adlin.orin.modules.alert.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

@Slf4j
@Component
public class SystemHealthCheckTask {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AlertService alertService;

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private int milvusPort;

    @Value("${orin.healthcheck.log-state-change-only:true}")
    private boolean logStateChangeOnly;

    private volatile boolean mysqlHealthy = true;
    private volatile boolean redisHealthy = true;
    private volatile boolean milvusHealthy = true;

    public SystemHealthCheckTask(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
            AlertService alertService) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.alertService = alertService;
    }

    /**
     * 每分钟检查一次外部依赖系统健康状况
     */
    @Scheduled(fixedRate = 60000)
    public void checkSystemHealth() {
        if (!logStateChangeOnly) {
            log.info("Starting system health check (MySQL, Redis, Milvus)...");
        }

        // 1. Check MySQL
        try {
            jdbcTemplate.execute("SELECT 1");
            if (!mysqlHealthy) {
                log.info("MySQL health recovered.");
            } else if (!logStateChangeOnly) {
                log.debug("MySQL health check passed.");
            }
            mysqlHealthy = true;
        } catch (Exception e) {
            if (mysqlHealthy || !logStateChangeOnly) {
                log.warn("MySQL health check failed: {}", e.getMessage());
            }
            if (mysqlHealthy) {
                alertService.triggerSystemAlert("SYSTEM_HEALTH", "EXTERNAL_DB", "MySQL 关系型数据库连接失败: " + e.getMessage());
            }
            mysqlHealthy = false;
        }

        // 2. Check Redis
        try {
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            if (!"PONG".equalsIgnoreCase(pingResult) && pingResult == null) {
                throw new RuntimeException("Redis ping returned unexpected result: " + pingResult);
            }
            if (!redisHealthy) {
                log.info("Redis health recovered.");
            } else if (!logStateChangeOnly) {
                log.debug("Redis health check passed.");
            }
            redisHealthy = true;
        } catch (Exception e) {
            if (redisHealthy || !logStateChangeOnly) {
                log.warn("Redis health check failed: {}", e.getMessage());
            }
            if (redisHealthy) {
                alertService.triggerSystemAlert("SYSTEM_HEALTH", "EXTERNAL_REDIS", "Redis 分布式缓存连接失败: " + e.getMessage());
            }
            redisHealthy = false;
        }

        // 3. Check Milvus
        try {
            // Use lightweight TCP probe to avoid Milvus SDK retry spam when the service is down.
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(milvusHost, milvusPort), 1500);
            }
            if (!milvusHealthy) {
                log.info("Milvus health recovered ({}:{})", milvusHost, milvusPort);
            } else if (!logStateChangeOnly) {
                log.debug("Milvus health check passed.");
            }
            milvusHealthy = true;
        } catch (Exception e) {
            if (milvusHealthy || !logStateChangeOnly) {
                log.warn("Milvus health check failed ({}:{}): {}", milvusHost, milvusPort, e.getMessage());
            }
            if (milvusHealthy) {
                alertService.triggerSystemAlert("SYSTEM_HEALTH", "EXTERNAL_MILVUS", "Milvus 向量数据库连接失败: " + e.getMessage());
            }
            milvusHealthy = false;
        }

        if (!logStateChangeOnly) {
            log.info("System health check completed.");
        }
    }
}
