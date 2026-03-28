package com.adlin.orin.common.exception;

import com.adlin.orin.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * F1.3 GlobalExceptionHandler 回归测试（纯单元测试）
 *
 * 测试目标：验证 GlobalExceptionHandler 对各类异常返回标准 Result 格式
 * - BusinessException → HTTP 500 + code + message + traceId
 * - ResourceNotFoundException → HTTP 404 + 20001
 * - ValidationException → HTTP 400 + 90001
 * - AuthenticationException → HTTP 401 + 70001
 * - AuthorizationException → HTTP 403 + 70004
 * - 未处理异常 → HTTP 500 + 10000 + traceId
 * - determineHttpStatus() 映射正确性
 *
 * 运行方式：mvn test -Dtest=GlobalExceptionHandlerTest
 *
 * 注意：使用纯 Mockito 单元测试，避免 @SpringBootTest 加载完整应用上下文
 * （完整上下文依赖 Milvus 等外部服务，在测试环境中不可用）
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        handler = new GlobalExceptionHandler();
        // 注入 test profile，使 isDevMode() 返回 true，确保 detail 字段被填充
        var field = GlobalExceptionHandler.class.getDeclaredField("activeProfile");
        field.setAccessible(true);
        field.set(handler, "dev");
        when(request.getRequestURI()).thenReturn("/test/path");
    }

    // ==================== BusinessException ====================

    @Test
    @DisplayName("F1.3 - BusinessException: 返回 500 + code=30001 + traceId")
    void testHandleBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.AGENT_NOT_FOUND, "智能体不存在");

        ResponseEntity<Result<Object>> response = handler.handleBusinessException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("30001", response.getBody().getCode());
        assertEquals("智能体不存在", response.getBody().getMessage());
        assertNotNull(response.getBody().getTraceId());
        assertEquals("/test/path", response.getBody().getPath());
    }

    @Test
    @DisplayName("F1.3 - BusinessException: 附带 details 时 metadata 不为空")
    void testHandleBusinessException_WithDetails() {
        Object details = Map.of("agentId", "agent-001");
        BusinessException ex = new BusinessException(ErrorCode.AGENT_NOT_FOUND, "智能体不存在", details);

        ResponseEntity<Result<Object>> response = handler.handleBusinessException(ex, request);

        assertNotNull(response.getBody().getMetadata());
    }

    // ==================== ResourceNotFoundException ====================

    @Test
    @DisplayName("F1.3 - ResourceNotFoundException: 返回 404 + code=20001")
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Agent", "agent-001");

        ResponseEntity<Result<Object>> response = handler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("20001", response.getBody().getCode());
        assertNotNull(response.getBody().getTraceId());
    }

    // ==================== ValidationException ====================

    @Test
    @DisplayName("F1.3 - ValidationException: 返回 400 + code=90001")
    void testHandleValidationException() {
        ValidationException ex = new ValidationException("字段验证失败");

        ResponseEntity<Result<Object>> response = handler.handleValidationException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("90001", response.getBody().getCode());
    }

    @Test
    @DisplayName("F1.3 - ValidationException: 带 fieldErrors 时 metadata 包含错误映射")
    void testHandleValidationException_WithFieldErrors() {
        Map<String, String> fieldErrors = Map.of("name", "名称不能为空", "email", "邮箱格式错误");
        ValidationException ex = new ValidationException("数据验证失败", fieldErrors);

        ResponseEntity<Result<Object>> response = handler.handleValidationException(ex, request);

        assertNotNull(response.getBody().getMetadata());
        @SuppressWarnings("unchecked")
        Map<String, String> meta = (Map<String, String>) response.getBody().getMetadata();
        assertEquals("名称不能为空", meta.get("name"));
        assertEquals("邮箱格式错误", meta.get("email"));
    }

    // ==================== AuthenticationException ====================

    @Test
    @DisplayName("F1.3 - AuthenticationException: 返回 401 + code=70001")
    void testHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("认证失败");

        ResponseEntity<Result<Object>> response = handler.handleAuthenticationException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("70001", response.getBody().getCode());
    }

    // ==================== AuthorizationException ====================

    @Test
    @DisplayName("F1.3 - AuthorizationException: 返回 403 + code=70004")
    void testHandleAuthorizationException() {
        AuthorizationException ex = new AuthorizationException("无权限");

        ResponseEntity<Result<Object>> response = handler.handleAuthorizationException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("70004", response.getBody().getCode());
    }

    // ==================== 未捕获异常 ====================

    @Test
    @DisplayName("F1.3 - 未处理异常: 返回 500 + code=10000")
    void testHandleException() {
        Exception ex = new RuntimeException("原始未捕获异常");

        ResponseEntity<Result<Object>> response = handler.handleException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("10000", response.getBody().getCode());
        assertEquals("系统内部错误", response.getBody().getMessage());
        assertNotNull(response.getBody().getTraceId());
    }

    // ==================== HTTP Status 映射 ====================

    @Test
    @DisplayName("F1.3 - determineHttpStatus: 2xxxx → 404, 7xxxx → 401, 9xxxx → 400")
    void testDetermineHttpStatus() throws Exception {
        // 通过 BusinessException 触发 determineHttpStatus
        // 2xxxx → NOT_FOUND
        BusinessException resNotFound = new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        ResponseEntity<Result<Object>> r1 = handler.handleBusinessException(resNotFound, request);
        assertEquals(HttpStatus.NOT_FOUND, r1.getStatusCode());

        // 7xxxx → UNAUTHORIZED
        BusinessException authFailed = new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        ResponseEntity<Result<Object>> r2 = handler.handleBusinessException(authFailed, request);
        assertEquals(HttpStatus.UNAUTHORIZED, r2.getStatusCode());

        // 70004 → FORBIDDEN（权限不足）
        BusinessException noPerm = new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS);
        ResponseEntity<Result<Object>> r3 = handler.handleBusinessException(noPerm, request);
        assertEquals(HttpStatus.FORBIDDEN, r3.getStatusCode());

        // 9xxxx → BAD_REQUEST
        BusinessException validation = new BusinessException(ErrorCode.VALIDATION_ERROR);
        ResponseEntity<Result<Object>> r4 = handler.handleBusinessException(validation, request);
        assertEquals(HttpStatus.BAD_REQUEST, r4.getStatusCode());
    }

    // ==================== traceId 生成与传递 ====================

    @Test
    @DisplayName("F1.3 - 响应中包含非空 traceId")
    void testTraceIdInResponse() {
        BusinessException ex = new BusinessException(ErrorCode.SYSTEM_ERROR);

        ResponseEntity<Result<Object>> response = handler.handleBusinessException(ex, request);

        assertNotNull(response.getBody().getTraceId());
        assertFalse(response.getBody().getTraceId().isEmpty());
    }
}
