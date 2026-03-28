package com.adlin.orin.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F1.3 BusinessException 回归测试
 *
 * 测试目标：验证 BusinessException 所有构造函数变体
 * - 只传 ErrorCode
 * - 传 ErrorCode + 自定义消息
 * - 传 ErrorCode + 自定义消息 + details
 * - 传 ErrorCode + Throwable
 * - 传 ErrorCode + 自定义消息 + Throwable
 * - getErrorCode() / getDetails() 正确返回值
 *
 * 运行方式：mvn test -Dtest=BusinessExceptionTest
 */
class BusinessExceptionTest {

    @Test
    @DisplayName("F1.3 - 构造函数(ErrorCode)：消息默认用枚举的 message")
    void testConstructor_ErrorCodeOnly() {
        BusinessException ex = new BusinessException(ErrorCode.AGENT_NOT_FOUND);

        assertEquals(ErrorCode.AGENT_NOT_FOUND, ex.getErrorCode());
        assertEquals("智能体未找到", ex.getMessage());
        assertNull(ex.getDetails());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("F1.3 - 构造函数(ErrorCode, String)：覆盖默认消息")
    void testConstructor_ErrorCodeAndCustomMessage() {
        BusinessException ex = new BusinessException(ErrorCode.WORKFLOW_EXECUTION_FAILED, "执行超时");

        assertEquals(ErrorCode.WORKFLOW_EXECUTION_FAILED, ex.getErrorCode());
        assertEquals("执行超时", ex.getMessage());
        assertNull(ex.getDetails());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("F1.3 - 构造函数(ErrorCode, String, Object)：设置 details")
    void testConstructor_WithDetails() {
        Object details = java.util.Map.of("agentId", "agent-001", "stage", "chat");
        BusinessException ex = new BusinessException(ErrorCode.AGENT_NOT_FOUND, "智能体不存在", details);

        assertEquals(ErrorCode.AGENT_NOT_FOUND, ex.getErrorCode());
        assertEquals("智能体不存在", ex.getMessage());
        assertNotNull(ex.getDetails());
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> d = (java.util.Map<String, String>) ex.getDetails();
        assertEquals("agent-001", d.get("agentId"));
    }

    @Test
    @DisplayName("F1.3 - 构造函数(ErrorCode, Throwable)：cause 可用")
    void testConstructor_ErrorCodeAndCause() {
        Throwable cause = new RuntimeException("underlying error");
        BusinessException ex = new BusinessException(ErrorCode.DATABASE_ERROR, cause);

        assertEquals(ErrorCode.DATABASE_ERROR, ex.getErrorCode());
        assertEquals("数据库操作失败", ex.getMessage()); // 来自枚举
        assertSame(cause, ex.getCause());
        assertNull(ex.getDetails());
    }

    @Test
    @DisplayName("F1.3 - 构造函数(ErrorCode, String, Throwable)：自定义消息 + cause")
    void testConstructor_ErrorCodeAndMessageAndCause() {
        Throwable cause = new IllegalStateException("state invalid");
        BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR, "字段验证失败", cause);

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertEquals("字段验证失败", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getDetails());
    }

    @Test
    @DisplayName("F1.3 - 所有子异常类可被 BusinessException 接收")
    void testSubExceptionTypes() {
        // 各模块子异常均继承 BusinessException，应被 GlobalExceptionHandler 统一处理
        assertTrue(new com.adlin.orin.common.exception.ResourceNotFoundException("资源", "r-1")
                instanceof BusinessException);
        assertTrue(new ValidationException("验证失败")
                instanceof BusinessException);
        assertTrue(new AuthenticationException("认证失败")
                instanceof BusinessException);
        assertTrue(new AuthorizationException("无权限")
                instanceof BusinessException);
    }
}
