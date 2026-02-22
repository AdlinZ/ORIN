package com.adlin.orin.modules.monitor.task;

import com.adlin.orin.modules.alert.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;

@Slf4j
@Component
public class SystemHealthCheckTask {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AlertService alertService;

    @Value("${orin.milvus.host:127.0.0.1}")
    private String milvusHost;

    @Value("${orin.milvus.port:19530}")
    private int milvusPort;

    @Value("${orin.milvus.token:}")
    private String milvusToken;

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
        log.info("Starting system health check (MySQL, Redis, Milvus)...");

        // 1. Check MySQL
        try {
            jdbcTemplate.execute("SELECT 1");
            log.debug("MySQL health check passed.");
        } catch (Exception e) {
            log.error("MySQL health check failed!", e);
            alertService.triggerSystemAlert("SYSTEM_HEALTH", "EXTERNAL_DB", "MySQL 关系型数据库连接失败: " + e.getMessage());
        }

        // 2. Check Redis
        try {
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            if (!"PONG".equalsIgnoreCase(pingResult) && pingResult == null) {
                throw new RuntimeException("Redis ping returned unexpected result: " + pingResult);
            }
            log.debug("Redis health check passed.");
        } catch (Exception e) {
            log.error("Redis health check failed!", e);
            alertService.triggerSystemAlert("SYSTEM_HEALTH", "EXTERNAL_REDIS", "Redis 分布式缓存连接失败: " + e.getMessage());
        }

        // 3. Check Milvus
        MilvusServiceClient milvusClient = null;
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusHost)
                    .withPort(milvusPort)
                    .withAuthorization("root", milvusToken)
                    .withConnectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .withKeepAliveTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            milvusClient = new MilvusServiceClient(connectParam);

            R<Boolean> response = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName("system_health_check_dummy")
                    .build());

            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Milvus health check failed with status: " + response.getStatus());
            }
            log.debug("Milvus health check passed.");
        } catch (Exception e) {
            log.error("Milvus health check failed!", e);
            alertService.triggerSystemAlert("SYSTEM_HEALTH", "EXTERNAL_MILVUS", "Milvus 向量数据库连接失败: " + e.getMessage());
        } finally {
            if (milvusClient != null) {
                try {
                    milvusClient.close(2);
                } catch (Exception e) {
                    // Ignore close exceptions
                }
            }
        }

        log.info("System health check completed.");
    }
}
