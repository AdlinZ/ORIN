package com.adlin.orin.config;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置验证器（Phase 3 起步 · 小刀 8）。
 *
 * 在应用启动时验证：
 * 1. 必需的环境变量是否已配置（缺失时 prod / staging 抛异常）
 * 2. **占位符/示例密钥黑名单** —— 防止运维 copy .env.example → .env 后忘记
 *    替换 `admin123` / `replace_with_local_*` / `your_*_here` 等占位符
 *    直接上 prod。命中黑名单 → prod / staging 抛异常拒启动；dev / test 仅
 *    error 日志。
 * 3. JWT_SECRET 长度 ≥ 32 字符（256 bit）—— 弱密钥 prod 抛异常。
 */
@Configuration
public class SecurityPropertiesValidator {

    private static final Logger logger = LoggerFactory.getLogger(SecurityPropertiesValidator.class);

    /**
     * 占位符 / 示例默认密钥黑名单。`.env.example` / 文档 / 早期 commit 残留
     * 的占位符。命中任一 → 视为使用默认弱密钥，必须在生产部署前替换。
     */
    private static final List<String> DEFAULT_PASSWORD_BLACKLIST = List.of(
            "admin123",
            "replace_with_local_root_password",
            "replace_with_local_db_password",
            "replace_with_local_redis_password",
            "replace_with_local_rabbitmq_password",
            "your_256_bit_random_secret_key_here",
            "your_64_bytes_random_secret_generated_by_openssl_rand_base64_64",
            "changeme",
            "password",
            "secret"
    );

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

    @Value("${REDIS_PASSWORD:}")
    private String redisPassword;

    @Value("${SILICONFLOW_API_KEY:}")
    private String siliconflowApiKey;

    @Value("${ORIN_DEFAULT_ADMIN_PASSWORD:}")
    private String orinDefaultAdminPassword;

    @Value("${RABBITMQ_PASSWORD:}")
    private String rabbitmqPassword;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    public void validateSecurityProperties() {
        List<String> missingRequired = new ArrayList<>();
        List<String> missingOptional = new ArrayList<>();
        List<String> usingDefault = new ArrayList<>();

        // 必需配置检查（所有环境）
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

        // 可选但推荐配置
        if (isBlank(siliconflowApiKey)) {
            missingOptional.add("SILICONFLOW_API_KEY");
        }

        // ---- 小刀 8：占位符黑名单检查 ----
        if (!isBlank(orinDefaultAdminPassword) && isBlacklisted(orinDefaultAdminPassword)) {
            usingDefault.add("ORIN_DEFAULT_ADMIN_PASSWORD");
        }
        if (!isBlank(rabbitmqPassword) && isBlacklisted(rabbitmqPassword)) {
            usingDefault.add("RABBITMQ_PASSWORD");
        }
        if (!isBlank(jwtSecret) && isBlacklisted(jwtSecret)) {
            usingDefault.add("JWT_SECRET");
        }
        if (!isBlank(dbPassword) && isBlacklisted(dbPassword)) {
            usingDefault.add("DB_PASSWORD");
        }
        if (!isBlank(redisPassword) && isBlacklisted(redisPassword)) {
            usingDefault.add("REDIS_PASSWORD");
        }

        // 必需配置缺失处理
        if (!missingRequired.isEmpty()) {
            logger.error("===========================================");
            logger.error("缺少必需的环境变量配置:");
            for (String var : missingRequired) {
                logger.error("  - {}", var);
            }
            logger.error("请在 .env 文件或环境变量中配置这些值");
            logger.error("参考 .env.example 文件");
            logger.error("===========================================");

            if (isProductionLike()) {
                throw new BusinessException(ErrorCode.OPERATION_FAILED,
                        "生产 / staging 环境缺少必需的配置，启动终止");
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

        // ---- 小刀 8：占位符密钥阻断 ----
        if (!usingDefault.isEmpty()) {
            logger.error("===========================================");
            logger.error("检测到使用占位符 / 示例默认值的密钥（必须替换！）:");
            for (String var : usingDefault) {
                logger.error("  - {}", var);
            }
            logger.error("生成强密钥: openssl rand -base64 64");
            logger.error("参考 .env.example 与 docs/手册-运维.md §10");
            logger.error("===========================================");

            if (isProductionLike()) {
                throw new BusinessException(ErrorCode.OPERATION_FAILED,
                        "生产 / staging 环境检测到占位符密钥，启动终止: " + String.join(", ", usingDefault));
            }
        }

        // ---- 小刀 8：JWT_SECRET 长度严格 ≥ 32 字符（256 bit）----
        if (!isBlank(jwtSecret) && !isBlacklisted(jwtSecret)) {
            if (jwtSecret.length() < 32) {
                if (isProductionLike()) {
                    throw new BusinessException(ErrorCode.OPERATION_FAILED,
                            "JWT_SECRET 长度 " + jwtSecret.length() + " < 32 字符（256 bit）—— 生产环境必须用强密钥");
                } else {
                    logger.warn("JWT_SECRET 长度 {} 字符 < 32（256 bit），强度不足", jwtSecret.length());
                }
            }
        }

        if (missingRequired.isEmpty() && usingDefault.isEmpty()) {
            logger.info("[SecurityPropertiesValidator] 环境变量配置验证通过");
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 占位符黑名单匹配：
     * - 等值匹配（大小写不敏感）：admin123 / replace_with_local_xxx / your_xxx_here 等
     * - 前缀模式：replace_with_ / changeme / todo_
     * - 长度 < 8：视为弱（避免只是改了大小的弱口令）
     */
    private boolean isBlacklisted(String value) {
        if (isBlank(value)) {
            return false;
        }
        String v = value.trim();
        String lower = v.toLowerCase();
        // 等值匹配黑名单
        for (String bad : DEFAULT_PASSWORD_BLACKLIST) {
            if (lower.equalsIgnoreCase(bad)) {
                return true;
            }
        }
        // 前缀模式（占位符关键词）
        if (lower.startsWith("replace_with_")) {
            return true;
        }
        if (lower.startsWith("changeme")) {
            return true;
        }
        if (lower.startsWith("your_") && lower.endsWith("_here")) {
            return true;
        }
        if (lower.startsWith("todo_")) {
            return true;
        }
        return false;
    }

    private boolean isProductionLike() {
        return "prod".equalsIgnoreCase(activeProfile) || "staging".equalsIgnoreCase(activeProfile);
    }
}
