package com.adlin.orin;

import com.adlin.orin.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

/**
 * H2-based security integration test base for authorization enforcement.
 *
 * <p>Uses the real Spring Security filter chain, real JWT tokens, real
 * {@code @PreAuthorize} annotations, and real service-layer ownership
 * checks. Database dialect (H2 vs MySQL) is irrelevant for authorization
 * logic — the full Spring Security → Controller → Service chain is tested.
 *
 * <p>No Docker required. Uses the existing {@code test} profile (H2 in-memory,
 * Flyway disabled, Hibernate ddl-auto=create-drop).
 *
 * <p>Seed users:
 * <ul>
 *   <li>{@code test-creator} (id=100) — ROLE_USER</li>
 *   <li>{@code test-operator} (id=101) — ROLE_OPERATOR</li>
 *   <li>{@code test-creator-2} (id=102) — ROLE_USER (different user)</li>
 * </ul>
 */
@AutoConfigureMockMvc
public abstract class H2SecurityIntegrationTest extends BaseTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate db;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected static final String CREATOR_ID = "100";
    protected static final String CREATOR_USERNAME = "test-creator";
    protected static final String OPERATOR_ID = "101";
    protected static final String OPERATOR_USERNAME = "test-operator";
    protected static final String CREATOR2_ID = "102";
    protected static final String CREATOR2_USERNAME = "test-creator-2";
    protected static final String TEST_PASSWORD = "test123";

    /**
     * Seed minimum test users and roles using H2-compatible SQL.
     * Tables are fresh from Hibernate create-drop, so no idempotency needed.
     */
    @BeforeEach
    void seedUsers() {
        // Clean up from previous test methods (tables persist across tests
        // within the same Spring context)
        db.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?)",
                Long.parseLong(CREATOR_ID), Long.parseLong(OPERATOR_ID), Long.parseLong(CREATOR2_ID));
        db.update("DELETE FROM sys_user WHERE user_id IN (?, ?, ?)",
                Long.parseLong(CREATOR_ID), Long.parseLong(OPERATOR_ID), Long.parseLong(CREATOR2_ID));

        String encodedPw = passwordEncoder.encode(TEST_PASSWORD);

        // Roles are already created by RoleService.ensureRoleExists() on startup.

        // Users
        db.update("INSERT INTO sys_user (user_id, username, password, status) VALUES (?, ?, ?, 'ENABLED')",
                Long.parseLong(CREATOR_ID), CREATOR_USERNAME, encodedPw);
        db.update("INSERT INTO sys_user (user_id, username, password, status) VALUES (?, ?, ?, 'ENABLED')",
                Long.parseLong(OPERATOR_ID), OPERATOR_USERNAME, encodedPw);
        db.update("INSERT INTO sys_user (user_id, username, password, status) VALUES (?, ?, ?, 'ENABLED')",
                Long.parseLong(CREATOR2_ID), CREATOR2_USERNAME, encodedPw);

        // User-role associations — resolve role_id from existing sys_role rows
        db.update("INSERT INTO sys_user_role (user_id, role_id) "
                        + "SELECT ?, role_id FROM sys_role WHERE role_code = ?",
                Long.parseLong(CREATOR_ID), "ROLE_USER");
        db.update("INSERT INTO sys_user_role (user_id, role_id) "
                        + "SELECT ?, role_id FROM sys_role WHERE role_code = ?",
                Long.parseLong(OPERATOR_ID), "ROLE_OPERATOR");
        db.update("INSERT INTO sys_user_role (user_id, role_id) "
                        + "SELECT ?, role_id FROM sys_role WHERE role_code = ?",
                Long.parseLong(CREATOR2_ID), "ROLE_USER");
    }

    /** Generate a signed JWT for the given user with specified roles. */
    protected String jwtFor(String userId, String username, String... roles) {
        return jwtService.generateToken(userId, username, Map.of("roles", List.of(roles)));
    }

    protected String jwtCreator() {
        return jwtFor(CREATOR_ID, CREATOR_USERNAME, "ROLE_USER");
    }

    protected String jwtCreator2() {
        return jwtFor(CREATOR2_ID, CREATOR2_USERNAME, "ROLE_USER");
    }

    protected String jwtOperator() {
        return jwtFor(OPERATOR_ID, OPERATOR_USERNAME, "ROLE_OPERATOR");
    }
}
