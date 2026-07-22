package com.adlin.orin;

import com.adlin.orin.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Integration test base class using Testcontainers MySQL.
 *
 * <p>Provides:
 * <ul>
 *   <li>Isolated MySQL 8.0 container with Flyway migrations applied</li>
 *   <li>JWT token generation helper for arbitrary user/role combinations</li>
 *   <li>MockMvc auto-configured with full Spring Security filter chain</li>
 *   <li>Test user/role seed data</li>
 * </ul>
 *
 * <p>Tests are tagged {@code integration} and excluded from default surefire runs.
 * Activate with: {@code mvn test -Pintegration-tests -Dtest="..."}
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("orindb")
            .withUsername("orin_test")
            .withPassword("orin_test_pass");

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
}
