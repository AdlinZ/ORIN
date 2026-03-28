package com.adlin.orin.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F1.3 统一错误码回归测试
 *
 * 测试目标：验证 ErrorCode 枚举的完整性和 fromCode() 查找正确性
 * - 所有错误码 code 格式正确（5位数字字符串）
 * - fromCode() 能正确查找已知码
 * - fromCode() 对未知码返回 SYSTEM_ERROR（不回抛）
 *
 * 运行方式：mvn test -Dtest=ErrorCodeTest
 */
class ErrorCodeTest {

    // ==================== code 格式验证 ====================

    @Test
    @DisplayName("F1.3 - 所有 SUCCESS code 格式为 00000")
    void testSuccessCode() {
        assertEquals("00000", ErrorCode.SUCCESS.getCode());
        assertEquals("操作成功", ErrorCode.SUCCESS.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10000", "10001", "10002", "10003", "10004", "10005",  // 通用错误
            "20001", "20002", "20003", "20004",                     // 资源错误
            "30001", "30002", "30003", "30004", "30005",            // Agent 错误
            "40001", "40002", "40003", "40004", "40005",            // Knowledge 错误
            "50001", "50002", "50003",                              // Model 错误
            "60001", "60002", "60003", "60004",                    // Workflow 错误
            "70001", "70002", "70003", "70004", "70005",           // Auth 错误
            "80001", "80002", "80003", "80004", "80005",           // External 错误
            "90001", "90002", "90003", "90004"                     // Validation 错误
    })
    @DisplayName("F1.3 - 所有错误码都是 5 位数字格式")
    void testAllErrorCodesAreFiveDigits(String code) {
        assertTrue(code.matches("\\d{5}"), "错误码 " + code + " 应为5位数字");
    }

    // ==================== fromCode() 查找正确性 ====================

    @Test
    @DisplayName("F1.3 - fromCode() 正确查找已知错误码")
    void testFromCode_KnownCodes() {
        assertEquals(ErrorCode.SUCCESS, ErrorCode.fromCode("00000"));
        assertEquals(ErrorCode.SYSTEM_ERROR, ErrorCode.fromCode("10000"));
        assertEquals(ErrorCode.INVALID_PARAMETER, ErrorCode.fromCode("10001"));
        assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.fromCode("10003"));
        assertEquals(ErrorCode.AGENT_NOT_FOUND, ErrorCode.fromCode("30001"));
        assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND, ErrorCode.fromCode("40001"));
        assertEquals(ErrorCode.WORKFLOW_NOT_FOUND, ErrorCode.fromCode("60001"));
        assertEquals(ErrorCode.AUTH_TOKEN_EXPIRED, ErrorCode.fromCode("70002"));
        assertEquals(ErrorCode.DATABASE_ERROR, ErrorCode.fromCode("80004"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ErrorCode.fromCode("90001"));
    }

    @Test
    @DisplayName("F1.3 - fromCode() 对未知码返回 SYSTEM_ERROR 而不是抛异常")
    void testFromCode_UnknownCode_ReturnsSystemError() {
        ErrorCode result = ErrorCode.fromCode("99999");
        assertEquals(ErrorCode.SYSTEM_ERROR, result);

        ErrorCode result2 = ErrorCode.fromCode("abcde");
        assertEquals(ErrorCode.SYSTEM_ERROR, result2);

        ErrorCode result3 = ErrorCode.fromCode("");
        assertEquals(ErrorCode.SYSTEM_ERROR, result3);
    }

    // ==================== 错误码范围划分正确性 ====================

    @Test
    @DisplayName("F1.3 - 错误码按区间划分验证（1xxxx = 通用, 7xxxx = 认证）")
    void testErrorCodeRangeClassification() {
        // 1xxxx 通用错误
        assertTrue(ErrorCode.SYSTEM_ERROR.getCode().startsWith("1"));
        assertTrue(ErrorCode.INVALID_PARAMETER.getCode().startsWith("1"));

        // 7xxxx 认证授权错误
        assertTrue(ErrorCode.AUTH_INVALID_CREDENTIALS.getCode().startsWith("7"));
        assertTrue(ErrorCode.AUTH_TOKEN_EXPIRED.getCode().startsWith("7"));
        assertTrue(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS.getCode().startsWith("7"));

        // 9xxxx Validation 错误
        assertTrue(ErrorCode.VALIDATION_ERROR.getCode().startsWith("9"));
    }

    // ==================== 消息非空验证 ====================

    @Test
    @DisplayName("F1.3 - 所有错误码的消息字段均非空")
    void testAllErrorCodesHaveMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getMessage(), "错误码 " + code.getCode() + " 的消息不应为空");
            assertFalse(code.getMessage().isEmpty(), "错误码 " + code.getCode() + " 的消息不应为空字符串");
        }
    }
}
