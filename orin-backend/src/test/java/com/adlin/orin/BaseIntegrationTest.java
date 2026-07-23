package com.adlin.orin;

import com.adlin.orin.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Integration test base class using Testcontainers MySQL.
 *
 * <p>Provides:
 * <ul>
 *   <li>Isolated MySQL 8.0 container pre-loaded with the V1..V87 baseline
 *       schema snapshot (see {@code db/orin-schema-V1-V87-baseline.sql}),
 *       then Flyway applies V88..V95 on top</li>
 *   <li>JWT token generation helper for arbitrary user/role combinations</li>
 *   <li>MockMvc auto-configured with full Spring Security filter chain</li>
 *   <li>Test user/role seed data</li>
 * </ul>
 *
 * <p>Why the baseline snapshot: the historical Flyway migrations V1..V87
 * are not idempotent from scratch (e.g. V5 expects {@code multimodal_files}
 * which V1 does not create). The canonical local/CI flow is to load the
 * snapshot then apply V88..V95; we replicate that here.
 *
 * <p>Tests are tagged {@code integration} and excluded from default surefire runs.
 * Activate with: {@code mvn test -Pintegration-tests -Dtest="..."}
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Testcontainers
@Import(BaseIntegrationTest.IntegrationTestStubBeans.class)
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("orindb")
            .withUsername("orin_test")
            .withPassword("orin_test_pass")
            // Pre-load V1..V87 baseline schema so Flyway only applies V88..V95
            // (combined with spring.flyway.baseline-on-migrate=true and
            // baseline-version=87 in application-integration-test.yml).
            .withInitScript("db/orin-schema-V1-V87-baseline.sql")
            // The mysqldump snapshot has CREATE TABLE ordering that references
            // tables not yet defined (e.g. gateway_routes → gateway_services).
            // Testcontainers' ScriptUtils strips the /*!40014 ... */ version
            // comments that originally disabled FOREIGN_KEY_CHECKS, so we set
            // the session variable on the JDBC connection URL instead. This
            // survives every Statement.execute() inside the init script.
            .withUrlParam("sessionVariables", "FOREIGN_KEY_CHECKS=0,UNIQUE_CHECKS=0");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Fixed test user IDs for predictable assertions
    protected static final String CREATOR_ID = "100";
    protected static final String CREATOR_USERNAME = "test-creator";
    protected static final String OPERATOR_ID = "101";
    protected static final String OPERATOR_USERNAME = "test-operator";
    protected static final String CREATOR2_ID = "102";
    protected static final String CREATOR2_USERNAME = "test-creator-2";
    protected static final String TEST_PASSWORD = "test123";

    /**
     * Seed minimum test users and roles into the Testcontainers MySQL.
     * Idempotent: uses INSERT IGNORE so repeated calls in subclasses are safe.
     */
    @BeforeEach
    void baseSeedUsers() {
        String encodedPw = passwordEncoder.encode(TEST_PASSWORD);

        // Roles — idempotent INSERT IGNORE
        jdbcTemplate.update(
                "INSERT IGNORE INTO sys_role (role_code, role_name, description) VALUES (?, ?, ?)",
                "ROLE_USER", "普通用户", "基础访问权限");
        jdbcTemplate.update(
                "INSERT IGNORE INTO sys_role (role_code, role_name, description) VALUES (?, ?, ?)",
                "ROLE_OPERATOR", "业务运营", "智能体业务配置、知识资产管理");

        // Users — idempotent with ON DUPLICATE KEY
        jdbcTemplate.update(
                "INSERT INTO sys_user (user_id, username, password, status) VALUES (?, ?, ?, 'ENABLED') "
                        + "ON DUPLICATE KEY UPDATE username = VALUES(username)",
                Long.parseLong(CREATOR_ID), CREATOR_USERNAME, encodedPw);
        jdbcTemplate.update(
                "INSERT INTO sys_user (user_id, username, password, status) VALUES (?, ?, ?, 'ENABLED') "
                        + "ON DUPLICATE KEY UPDATE username = VALUES(username)",
                Long.parseLong(OPERATOR_ID), OPERATOR_USERNAME, encodedPw);
        jdbcTemplate.update(
                "INSERT INTO sys_user (user_id, username, password, status) VALUES (?, ?, ?, 'ENABLED') "
                        + "ON DUPLICATE KEY UPDATE username = VALUES(username)",
                Long.parseLong(CREATOR2_ID), CREATOR2_USERNAME, encodedPw);

        // User-role associations — idempotent
        jdbcTemplate.update(
                "INSERT IGNORE INTO sys_user_role (user_id, role_id) "
                        + "SELECT ?, role_id FROM sys_role WHERE role_code = ?",
                Long.parseLong(CREATOR_ID), "ROLE_USER");
        jdbcTemplate.update(
                "INSERT IGNORE INTO sys_user_role (user_id, role_id) "
                        + "SELECT ?, role_id FROM sys_role WHERE role_code = ?",
                Long.parseLong(OPERATOR_ID), "ROLE_OPERATOR");
        jdbcTemplate.update(
                "INSERT IGNORE INTO sys_user_role (user_id, role_id) "
                        + "SELECT ?, role_id FROM sys_role WHERE role_code = ?",
                Long.parseLong(CREATOR2_ID), "ROLE_USER");
    }

    // ---- JWT helpers ----

    /**
     * Generate a signed JWT for the given user with the specified roles.
     * The token subject is set to {@code userId}, and the roles are embedded
     * in the {@code roles} claim as a String list.
     */
    protected String jwtFor(String userId, String username, String... roles) {
        return jwtService.generateToken(userId, username, Map.of("roles", List.of(roles)));
    }

    /** Convenience: JWT for the ROLE_USER test-creator. */
    protected String jwtCreator() {
        return jwtFor(CREATOR_ID, CREATOR_USERNAME, "ROLE_USER");
    }

    /** Convenience: JWT for the ROLE_USER test-creator-2 (different user). */
    protected String jwtCreator2() {
        return jwtFor(CREATOR2_ID, CREATOR2_USERNAME, "ROLE_USER");
    }

    /** Convenience: JWT for the ROLE_OPERATOR test-operator. */
    protected String jwtOperator() {
        return jwtFor(OPERATOR_ID, OPERATOR_USERNAME, "ROLE_OPERATOR");
    }

    /**
     * Stub configuration that supplies a Mockito-mocked
     * {@link org.springframework.data.redis.connection.RedisConnectionFactory}
     * bean so that beans like {@code RedisConfig#redisTemplate} (which
     * constructor-injects the connection factory) and
     * {@code UnifiedGatewayStatsService} can be created in tests where
     * {@code integration-test} excludes {@code RedisAutoConfiguration}.
     * The mock returns Mockito defaults (null/0/false) for every operation;
     * F02 tests never exercise the actual Redis-backed code paths.
     */
    @TestConfiguration
    static class IntegrationTestStubBeans {

        @Bean
        @Primary
        public RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        public org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate() {
            return mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        }
    }
}
