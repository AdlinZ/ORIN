package com.adlin.orin.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F02 AgentVersion / SecretReference / Idempotency 错误码 → HTTP 状态映射测试。
 * <p>
 * 锁定 ADR-002 v4.1 §D-2.11.4 中"ErrorCode 注册是 R3 层 PR review 验收项"的语义；
 * 防止后续 PR 误把新加的 3xxxx 系列掉回 500 默认值。
 */
@DisplayName("F02 AgentVersion ErrorCode → HTTP status 显式映射")
class AgentVersionErrorCodeStatusMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("AGENT_VERSION_FROZEN → 409 CONFLICT")
    void frozen_returnsConflict() {
        assertEquals(HttpStatus.CONFLICT, handler.determineHttpStatus(ErrorCode.AGENT_VERSION_FROZEN));
    }

    @Test
    @DisplayName("AGENT_VERSION_DELETE_FORBIDDEN → 405 METHOD_NOT_ALLOWED")
    void deleteForbidden_returnsMethodNotAllowed() {
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, handler.determineHttpStatus(ErrorCode.AGENT_VERSION_DELETE_FORBIDDEN));
    }

    @Test
    @DisplayName("AGENT_VERSION_NOT_FOUND → 404 NOT_FOUND")
    void versionNotFound_returns404() {
        assertEquals(HttpStatus.NOT_FOUND, handler.determineHttpStatus(ErrorCode.AGENT_VERSION_NOT_FOUND));
    }

    @Test
    @DisplayName("RUN_VERSION_RETIRED → 409 CONFLICT")
    void runVersionRetired_returnsConflict() {
        assertEquals(HttpStatus.CONFLICT, handler.determineHttpStatus(ErrorCode.RUN_VERSION_RETIRED));
    }

    @Test
    @DisplayName("IDEMPOTENCY_KEY_CONFLICT → 409 CONFLICT")
    void idempotencyKeyConflict_returnsConflict() {
        assertEquals(HttpStatus.CONFLICT, handler.determineHttpStatus(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    @DisplayName("SNAPSHOT_SCHEMA_INCOMPATIBLE → 422 UNPROCESSABLE_ENTITY")
    void snapshotSchemaIncompatible_returns422() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handler.determineHttpStatus(ErrorCode.SNAPSHOT_SCHEMA_INCOMPATIBLE));
    }

    @Test
    @DisplayName("SNAPSHOT_CANONICALIZE_FAILED → 500 INTERNAL_SERVER_ERROR")
    void snapshotCanonicalizeFailed_returns500() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, handler.determineHttpStatus(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED));
    }

    @Test
    @DisplayName("SECRET_REFERENCE_NOT_FOUND → 404 NOT_FOUND")
    void secretReferenceNotFound_returns404() {
        assertEquals(HttpStatus.NOT_FOUND, handler.determineHttpStatus(ErrorCode.SECRET_REFERENCE_NOT_FOUND));
    }

    @Test
    @DisplayName("RUNNER_LOCAL_SECRET_MISSING → 422 UNPROCESSABLE_ENTITY")
    void runnerLocalSecretMissing_returns422() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handler.determineHttpStatus(ErrorCode.RUNNER_LOCAL_SECRET_MISSING));
    }

    @Test
    @DisplayName("MISSING_IDEMPOTENCY_KEY → 400 BAD_REQUEST")
    void missingIdempotencyKey_returns400() {
        assertEquals(HttpStatus.BAD_REQUEST, handler.determineHttpStatus(ErrorCode.MISSING_IDEMPOTENCY_KEY));
    }

    @Test
    @DisplayName("AGENT_DRAFT_INVALID → 400 BAD_REQUEST")
    void agentDraftInvalid_returns400() {
        assertEquals(HttpStatus.BAD_REQUEST, handler.determineHttpStatus(ErrorCode.AGENT_DRAFT_INVALID));
    }

    @Test
    @DisplayName("F02 显式映射表与 ErrorCode 同步：避免 ErrorCode 增列但映射表漏登")
    void explicitMappingTable_tracksErrorCodeEntries() throws Exception {
        Map<String, HttpStatus> explicit = readExplicitMappingTable();
        Map<String, HttpStatus> codeToStatusFromEnum = new HashMap<>();

        // F02 范围枚举 30006..30016
        for (ErrorCode code : ErrorCode.values()) {
            String c = code.getCode();
            if (c.compareTo("30006") >= 0 && c.compareTo("30017") <= 0) {
                HttpStatus mapped = handler.determineHttpStatus(code);
                assertNotNull(mapped, "ErrorCode " + c + " 缺少 HTTP 映射");
                codeToStatusFromEnum.put(c, mapped);
            }
        }
        assertEquals(codeToStatusFromEnum, explicit,
                "显式映射表与 ErrorCode.determineHttpStatus 实际行为不一致");
    }

    @SuppressWarnings("unchecked")
    private Map<String, HttpStatus> readExplicitMappingTable() throws Exception {
        Field f = GlobalExceptionHandler.class.getDeclaredField("EXPLICIT_AGENT_VERSION_STATUS");
        f.setAccessible(true);
        return (Map<String, HttpStatus>) f.get(null);
    }
}
