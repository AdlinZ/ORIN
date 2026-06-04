package com.adlin.orin.modules.setup.service;

import com.adlin.orin.modules.setup.dto.SetupDtos.SetupCheck;
import com.adlin.orin.modules.setup.dto.SetupDtos.SetupStatusResponse;
import com.adlin.orin.modules.system.entity.SystemConfigEntity;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import com.adlin.orin.security.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SetupStatusService {

    public static final String SETUP_COMPLETED_KEY = "system.setup.completed";

    private final SystemConfigRepository systemConfigRepository;
    private final DataSource dataSource;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider;
    private final EncryptionUtil encryptionUtil;
    private final Environment environment;

    @Value("${orin.setup.enabled:${ORIN_SETUP_ENABLED:}}")
    private String setupEnabledOverride;

    @Value("${orin.ai-engine.url:http://127.0.0.1:8000}")
    private String aiEngineUrl;

    @Value("${jwt.secret:${JWT_SECRET:}}")
    private String jwtSecret;

    @Value("${orin.security.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${orin.default-admin.password:${ORIN_DEFAULT_ADMIN_PASSWORD:}}")
    private String defaultAdminPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    public SetupStatusResponse getStatus() {
        boolean completed = isSetupCompleted();
        boolean setupEnabled = isSetupWriteEnabled();
        return SetupStatusResponse.builder()
                .completed(completed)
                .setupEnabled(setupEnabled)
                .canInitialize(setupEnabled && !completed)
                .environment(currentProfile())
                .dependencies(buildDependencyChecks())
                .security(buildSecurityChecks())
                .build();
    }

    public boolean isSetupCompleted() {
        return systemConfigRepository.findByConfigKey(SETUP_COMPLETED_KEY)
                .map(SystemConfigEntity::getConfigValue)
                .map("true"::equalsIgnoreCase)
                .orElse(false);
    }

    public boolean isSetupWriteEnabled() {
        String configured = setupEnabledOverride != null ? setupEnabledOverride.trim() : "";
        if (!configured.isBlank()) {
            return Boolean.parseBoolean(configured);
        }
        return !isProdProfile();
    }

    public void markSetupCompleted() {
        SystemConfigEntity entity = systemConfigRepository.findByConfigKey(SETUP_COMPLETED_KEY)
                .orElseGet(SystemConfigEntity::new);
        entity.setConfigKey(SETUP_COMPLETED_KEY);
        entity.setConfigValue("true");
        entity.setDescription("首次部署初始化是否已完成");
        entity.setUpdatedAt(LocalDateTime.now());
        systemConfigRepository.save(entity);
    }

    private List<SetupCheck> buildDependencyChecks() {
        List<SetupCheck> checks = new ArrayList<>();
        checks.add(databaseCheck());
        checks.add(redisCheck());
        checks.add(aiEngineCheck());
        checks.add(rabbitCheck());
        return checks;
    }

    private SetupCheck databaseCheck() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(1);
            return check("database", "MySQL / 数据库", valid ? "ok" : "error", "error", true,
                    valid ? "数据库连接可用" : "数据库连接不可用");
        } catch (SQLException e) {
            return check("database", "MySQL / 数据库", "error", "error", true, "数据库连接失败");
        }
    }

    private SetupCheck redisCheck() {
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
                return check("redis", "Redis", "error", "error", true, "Redis 未配置");
            }
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return check("redis", "Redis", "PONG".equalsIgnoreCase(pong) ? "ok" : "error", "error", true,
                    "PONG".equalsIgnoreCase(pong) ? "Redis 连接可用" : "Redis ping 无响应");
        } catch (Exception e) {
            return check("redis", "Redis", "error", "error", true, "Redis 连接失败");
        }
    }

    private SetupCheck aiEngineCheck() {
        try {
            String url = aiEngineUrl.replaceAll("/+$", "") + "/health";
            restTemplate.getForEntity(url, String.class);
            return check("ai-engine", "AI Engine", "ok", "warning", false, "AI Engine 健康检查可达");
        } catch (Exception e) {
            return check("ai-engine", "AI Engine", "warning", "warning", false, "AI Engine 暂不可达，可稍后配置");
        }
    }

    private SetupCheck rabbitCheck() {
        ConnectionFactory factory = rabbitConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            return check("rabbitmq", "RabbitMQ", "warning", "warning", false, "RabbitMQ 未配置，队列能力会降级");
        }
        try (Connection connection = factory.createConnection()) {
            return check("rabbitmq", "RabbitMQ", connection.isOpen() ? "ok" : "warning", "warning", false,
                    connection.isOpen() ? "RabbitMQ 连接可用" : "RabbitMQ 连接未打开");
        } catch (Exception e) {
            return check("rabbitmq", "RabbitMQ", "warning", "warning", false, "RabbitMQ 暂不可达，队列能力会降级");
        }
    }

    private List<SetupCheck> buildSecurityChecks() {
        List<SetupCheck> checks = new ArrayList<>();
        checks.add(check("jwt-secret", "JWT_SECRET", isBlank(jwtSecret) ? "error" : jwtSecret.length() < 32 ? "warning" : "ok",
                isBlank(jwtSecret) ? "error" : "warning", true,
                isBlank(jwtSecret) ? "JWT_SECRET 未配置" : jwtSecret.length() < 32 ? "JWT_SECRET 长度偏短" : "JWT_SECRET 已配置"));
        boolean wildcardCors = Arrays.stream((allowedOrigins == null ? "" : allowedOrigins).split(","))
                .map(String::trim)
                .anyMatch("*"::equals);
        checks.add(check("cors", "CORS_ALLOWED_ORIGINS", wildcardCors ? "error" : "ok", "error", true,
                wildcardCors ? "CORS 不应使用 *" : "CORS 未使用通配符"));
        checks.add(check("default-admin-password", "ORIN_DEFAULT_ADMIN_PASSWORD",
                isBlank(defaultAdminPassword) ? "ok" : "warning", "warning", false,
                isBlank(defaultAdminPassword) ? "未配置默认管理员密码" : "检测到默认管理员 bootstrap 密码，初始化后请移除"));
        checks.add(check("encryption-key", "ENCRYPTION_KEY",
                encryptionUtil.isEncryptionEnabled() ? "ok" : "warning", "warning", false,
                encryptionUtil.isEncryptionEnabled() ? "密钥加密已启用" : "未配置 ENCRYPTION_KEY，保存 Provider Key 前应先配置"));
        return checks;
    }

    private SetupCheck check(String key, String name, String status, String severity, boolean required, String message) {
        return SetupCheck.builder()
                .key(key)
                .name(name)
                .status(status)
                .severity(severity)
                .required(required)
                .message(message)
                .build();
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private String currentProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
