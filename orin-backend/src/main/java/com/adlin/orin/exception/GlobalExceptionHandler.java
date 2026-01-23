package com.adlin.orin.exception;

import com.adlin.orin.common.dto.ErrorResponse;
import com.adlin.orin.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局异常处理器
 * 统一处理所有异常并返回标准化的错误响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Business exception: code={}, message={}",
                traceId, ex.getErrorCode().getCode(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .detail(isDevMode() ? ex.getErrorCode().getMessage() : null)
                .path(request.getRequestURI())
                .status(determineHttpStatus(ex.getErrorCode()).value())
                .metadata(ex.getDetails())
                .traceId(traceId)
                .build();

        return ResponseEntity
                .status(determineHttpStatus(ex.getErrorCode()))
                .body(response);
    }

    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Resource not found: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理验证异常
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Validation exception: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .metadata(ex.getDetails())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Authentication failed: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .status(HttpStatus.UNAUTHORIZED.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 处理授权异常
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(
            AuthorizationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Authorization failed: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .status(HttpStatus.FORBIDDEN.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 处理工作流执行异常
     */
    @ExceptionHandler(WorkflowExecutionException.class)
    public ResponseEntity<ErrorResponse> handleWorkflowException(
            WorkflowExecutionException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.error("[TraceId: {}] Workflow execution failed: {}", traceId, ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.WORKFLOW_EXECUTION_FAILED.getCode())
                .message(ex.getMessage())
                .detail(isDevMode() ? getStackTrace(ex) : null)
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理向量化异常
     */
    @ExceptionHandler(VectorizationException.class)
    public ResponseEntity<ErrorResponse> handleVectorizationException(
            VectorizationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.error("[TraceId: {}] Vectorization failed: {}", traceId, ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.VECTORIZATION_FAILED.getCode())
                .message(ex.getMessage())
                .detail(isDevMode() ? getStackTrace(ex) : null)
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * 处理Spring Validation异常（@Valid注解触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("[TraceId: {}] Validation failed: {}", traceId, fieldErrors);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message("数据验证失败")
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .metadata(fieldErrors)
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        Map<String, String> violations = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            violations.put(propertyPath, message);
        }

        log.warn("[TraceId: {}] Constraint violation: {}", traceId, violations);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message("约束验证失败")
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .metadata(violations)
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        String message = String.format("参数 '%s' 的值 '%s' 类型不正确",
                ex.getName(), ex.getValue());

        log.warn("[TraceId: {}] Type mismatch: {}", traceId, message);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message(message)
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.error("[TraceId: {}] Runtime exception: {}", traceId, ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.SYSTEM_ERROR.getCode())
                .message("系统内部错误")
                .detail(isDevMode() ? ex.getMessage() : "请联系系统管理员")
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.error("[TraceId: {}] Unhandled exception: {}", traceId, ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.SYSTEM_ERROR.getCode())
                .message("系统发生未知错误")
                .detail(isDevMode() ? ex.getMessage() : "请联系系统管理员")
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 根据错误代码确定HTTP状态码
     */
    private HttpStatus determineHttpStatus(ErrorCode errorCode) {
        String code = errorCode.getCode();

        // 2xxxx - 资源相关 -> 404
        if (code.startsWith("2")) {
            return HttpStatus.NOT_FOUND;
        }
        // 7xxxx - 认证授权 -> 401/403
        if (code.startsWith("7")) {
            if (code.equals("70004")) {
                return HttpStatus.FORBIDDEN;
            }
            return HttpStatus.UNAUTHORIZED;
        }
        // 9xxxx - 验证错误 -> 400
        if (code.startsWith("9")) {
            return HttpStatus.BAD_REQUEST;
        }
        // 其他 -> 500
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 判断是否为开发模式
     */
    private boolean isDevMode() {
        return "dev".equalsIgnoreCase(activeProfile);
    }

    /**
     * 获取异常堆栈信息
     */
    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 2000) { // 限制长度
                sb.append("...(truncated)");
                break;
            }
        }
        return sb.toString();
    }
}
