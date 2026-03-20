package com.adlin.orin.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置验证器
 * 在应用启动时验证必需的环境变量是否已配置
 */
@Configuration
public class SecurityPropertiesValidator {

    private static final Logger logger = LoggerFactory.getLogger(SecurityPropertiesValidator.class);

    @Value("${DB_HOST:}")
    private String dbHost;

    @Value("${DB_USERNAME:}")
    private String dbUsername;

    @Value("${DB_PASSWORD:}")
    private String dbPassword;

    @Value("${JWT_SECRET:}")
    private String jwtSecret;

    @Value("${REDIS_HOST:}")
    private String redisHost;

    @Value("${SILICONFLOW_API_KEY:}")
    private String siliconflowApiKey;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    public void validateSecurityProperties() {
        List<String> missingRequired = new ArrayList<>();
        List<String> missingOptional = new ArrayList<>();

        // 检查必需的配置（所有环境）
        if (isBlank(dbHost)) {
            missingRequired.add("DB_HOST");
        }
        if (isBlank(dbUsername)) {
            missingRequired.add("DB_USERNAME");
        }
        if (isBlank(dbPassword)) {
            missingRequired.add("DB_PASSWORD");
        }
        if (isBlank(jwtSecret)) {
            missingRequired.add("JWT_SECRET");
        }
        if (isBlank(redisHost)) {
            missingRequired.add("REDIS_HOST");
        }

        // 检查可选但推荐的配置
        if (isBlank(siliconflowApiKey)) {
            missingOptional.add("SILICONFLOW_API_KEY");
        }

        // 根据环境输出不同的日志级别
        if (!missingRequired.isEmpty()) {
            logger.error("===========================================");
            logger.error("缺少必需的环境变量配置:");
            for (String var : missingRequired) {
                logger.error("  - {}", var);
            }
            logger.error("请在 .env 文件或环境变量中配置这些值");
            logger.error("参考 .env.example 文件");
            logger.error("===========================================");

            // 生产环境必须停止启动
            if ("prod".equalsIgnoreCase(activeProfile)) {
                throw new IllegalStateException("生产环境缺少必需的配置，启动终止");
            }
        }

        if (!missingOptional.isEmpty()) {
            logger.warn("===========================================");
            logger.warn("建议配置以下可选变量以启用完整功能:");
            for (String var : missingOptional) {
                logger.warn("  - {}", var);
            }
            logger.warn("===========================================");
        }

        // 检查 JWT 密钥安全性
        if (!isBlank(jwtSecret)) {
            if (jwtSecret.length() < 32) {
                logger.warn("JWT_SECRET 长度小于 32 字符，不够安全");
            }
            if (jwtSecret.contains("dev") || jwtSecret.contains("test") || jwtSecret.contains("secret")) {
                logger.warn("JWT_SECRET 包含不安全的关键字，请使用随机生成的密钥");
            }
        }

        if (missingRequired.isEmpty()) {
            logger.info("✓ 环境变量配置验证通过");
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
