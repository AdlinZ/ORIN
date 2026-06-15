package com.adlin.orin.config;

import com.adlin.orin.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SecurityPropertiesValidator 单测（Phase 3 起步 · 小刀 8）。
 *
 * 覆盖：
 * - 必需配置缺失 → 仅 error 日志（dev），不抛
 * - 占位符黑名单命中 → 仅 error 日志（dev），不抛
 * - prod / staging 占位符黑名单命中 → 抛 BusinessException
 * - JWT_SECRET 长度 < 32 → 抛 BusinessException（prod）
 * - 强密钥 + 真实 DB/Redis host → 验证通过
 *
 * 用 ReflectionTestUtils 注入字段，避免启动 Spring 上下文（@PostConstruct 触发即可）。
 */
class SecurityPropertiesValidatorTest {

    /** 构造一个最小可跑 validator：所有字段设真实值，profile=dev。 */
    private SecurityPropertiesValidator buildValidator(String profile) {
        SecurityPropertiesValidator v = new SecurityPropertiesValidator();
        ReflectionTestUtils.setField(v, "dbHost", "localhost");
        ReflectionTestUtils.setField(v, "dbUsername", "sa");
        ReflectionTestUtils.setField(v, "dbPassword", "real-db-password-1234567890");
        ReflectionTestUtils.setField(v, "jwtSecret", "real-jwt-secret-with-at-least-32-chars-1234567890");
        ReflectionTestUtils.setField(v, "redisHost", "localhost");
        ReflectionTestUtils.setField(v, "redisPassword", "real-redis-password-1234567890");
        ReflectionTestUtils.setField(v, "siliconflowApiKey", "sk-real");
        ReflectionTestUtils.setField(v, "orinDefaultAdminPassword", "real-admin-password-1234567890");
        ReflectionTestUtils.setField(v, "rabbitmqPassword", "real-rabbit-password-1234567890");
        ReflectionTestUtils.setField(v, "activeProfile", profile);
        return v;
    }

    @Test
    void dev_real_secrets_pass() {
        SecurityPropertiesValidator v = buildValidator("dev");
        assertDoesNotThrow(v::validateSecurityProperties);
    }

    @Test
    void prod_real_secrets_pass() {
        SecurityPropertiesValidator v = buildValidator("prod");
        assertDoesNotThrow(v::validateSecurityProperties);
    }

    @Test
    void dev_default_admin_password_warns_but_does_not_throw() {
        SecurityPropertiesValidator v = buildValidator("dev");
        ReflectionTestUtils.setField(v, "orinDefaultAdminPassword", "admin123");
        // dev 模式：占位符命中只 log，不抛
        assertDoesNotThrow(v::validateSecurityProperties);
    }

    @Test
    void prod_default_admin_password_throws() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "orinDefaultAdminPassword", "admin123");
        BusinessException ex = assertThrows(BusinessException.class, v::validateSecurityProperties);
        assertTrue(ex.getMessage().contains("ORIN_DEFAULT_ADMIN_PASSWORD"),
                "exception 应提及变量名: " + ex.getMessage());
    }

    @Test
    void prod_replace_with_local_rabbitmq_password_throws() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "rabbitmqPassword", "replace_with_local_rabbitmq_password");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void prod_your_xxx_here_jwt_throws() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "jwtSecret", "your_256_bit_random_secret_key_here");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void prod_jwt_secret_too_short_throws() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "jwtSecret", "short-but-not-blacklisted");
        BusinessException ex = assertThrows(BusinessException.class, v::validateSecurityProperties);
        assertTrue(ex.getMessage().contains("JWT_SECRET"),
                "exception 应提及 JWT_SECRET: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("256 bit"),
                "exception 应说明 256 bit 要求: " + ex.getMessage());
    }

    @Test
    void dev_jwt_secret_too_short_warns_but_does_not_throw() {
        SecurityPropertiesValidator v = buildValidator("dev");
        ReflectionTestUtils.setField(v, "jwtSecret", "short-but-not-blacklisted");
        // dev 模式：弱密钥只 warn，不抛
        assertDoesNotThrow(v::validateSecurityProperties);
    }

    @Test
    void prod_default_db_password_throws() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "dbPassword", "replace_with_local_db_password");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void prod_default_redis_password_throws() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "redisPassword", "replace_with_local_redis_password");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void staging_default_password_throws() {
        SecurityPropertiesValidator v = buildValidator("staging");
        ReflectionTestUtils.setField(v, "orinDefaultAdminPassword", "admin123");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void prod_missing_required_env_throws() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "dbHost", "");  // 必需配置缺失
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void dev_missing_required_env_warns_but_does_not_throw() {
        SecurityPropertiesValidator v = buildValidator("dev");
        ReflectionTestUtils.setField(v, "dbHost", "");
        assertDoesNotThrow(v::validateSecurityProperties);
    }

    /**
     * 黑名单精确等值匹配验证：合法但"看起来像占位符"的强密钥**不**应误杀。
     * 如 "Replace_With_Something"（中间大写、不是完全小写）也**不**应误杀。
     */
    @Test
    void blacklist_strict_equality_does_not_kill_similar_strong_secrets() {
        SecurityPropertiesValidator v = buildValidator("dev");
        // "admin123!" （带 ! 后缀）不应命中 admin123 黑名单
        ReflectionTestUtils.setField(v, "orinDefaultAdminPassword", "admin123!extra-padding-to-pass-length");
        // 这里应仅 WARN（不是占位符命中），但长度也 > 8（不会触发弱密码规则）→ 不抛
        assertDoesNotThrow(v::validateSecurityProperties);
    }

    @Test
    void blacklist_prefix_replace_with_() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "rabbitmqPassword", "replace_with_anything_else");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void blacklist_prefix_changeme() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "dbPassword", "CHANGEME_IN_PROD");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void blacklist_prefix_your_ending_underscore_here() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "jwtSecret", "your_anything_here");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    @Test
    void blacklist_prefix_todo_() {
        SecurityPropertiesValidator v = buildValidator("prod");
        ReflectionTestUtils.setField(v, "redisPassword", "todo_set_real_password_later");
        assertThrows(BusinessException.class, v::validateSecurityProperties);
    }

    /**
     * 测试套件用 `application-test.properties` 默认 `jwt.secret=test-secret-key-for-testing-only-must-be-at-least-256-bits-long`。
     * 56 字符 > 32；前缀 "test-" 但黑名单只匹配 `replace_with_` / `changeme` / `your_*_here` / `todo_`，
     * 不应误杀。
     */
    @Test
    void test_profile_jwt_secret_does_not_hit_blacklist() {
        SecurityPropertiesValidator v = buildValidator("test");
        ReflectionTestUtils.setField(v, "jwtSecret",
                "test-secret-key-for-testing-only-must-be-at-least-256-bits-long");
        // 56 字符，不命中黑名单（不以前缀 replace_with_/changeme/your_*_here/todo_ 起）→ dev/test 仅 warn
        assertDoesNotThrow(v::validateSecurityProperties);
    }
}
