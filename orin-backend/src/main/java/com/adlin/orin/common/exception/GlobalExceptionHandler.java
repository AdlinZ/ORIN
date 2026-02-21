package com.adlin.orin.common.exception;

import com.adlin.orin.common.dto.Result;
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
 * 统一处理所有异常并返回标准化的 Result 格式
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
    public ResponseEntity<Result<Object>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Business exception: code={}, message={}",
                traceId, ex.getErrorCode().getCode(), ex.getMessage());

        Result<Object> response = Result.<Object>builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .detail(isDevMode() ? ex.getErrorCode().getMessage() : null)
                .path(request.getRequestURI())
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
    public ResponseEntity<Result<Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Resource not found: {}", traceId, ex.getMessage());

        Result<Object> response = Result.<Object>builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理验证异常
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Object>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Validation exception: {}", traceId, ex.getMessage());

        Result<Object> response = Result.<Object>builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .metadata(ex.getDetails())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Object>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Authentication failed: {}", traceId, ex.getMessage());

        Result<Object> response = Result.<Object>builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 处理授权异常
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<Result<Object>> handleAuthorizationException(
            AuthorizationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.warn("[TraceId: {}] Authorization failed: {}", traceId, ex.getMessage());

        Result<Object> response = Result.<Object>builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 处理参数映射异常 (@Valid 触发)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("[TraceId: {}] Method argument validation failed: {}", traceId, fieldErrors);

        Result<Object> response = Result.<Object>builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message("数据验证失败")
                .path(request.getRequestURI())
                .metadata(fieldErrors)
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        Map<String, String> violations = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            violations.put(propertyPath, message);
        }

        log.warn("[TraceId: {}] Constraint violation: {}", traceId, violations);

        Result<Object> response = Result.<Object>builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message("约束验证失败")
                .path(request.getRequestURI())
                .metadata(violations)
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        String message = String.format("参数 '%s' 的值 '%s' 类型不正确",
                ex.getName(), ex.getValue());

        log.warn("[TraceId: {}] Type mismatch: {}", traceId, message);

        Result<Object> response = Result.<Object>builder()
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message(message)
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(
            Exception ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.error("[TraceId: {}] Internal server error: ", traceId, ex);

        ErrorCode errorCode = ErrorCode.SYSTEM_ERROR;
        Result<Object> response = Result.<Object>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .detail(isDevMode() ? ex.getMessage() : "请联系管理员并提供 TraceId: " + traceId)
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 根据 ErrorCode 确定 HTTP 状态码
     */
    private HttpStatus determineHttpStatus(ErrorCode errorCode) {
        String code = errorCode.getCode();
        if (code.startsWith("2"))
            return HttpStatus.NOT_FOUND;
        if (code.startsWith("7")) {
            if (code.equals("70004"))
                return HttpStatus.FORBIDDEN;
            return HttpStatus.UNAUTHORIZED;
        }
        if (code.startsWith("9"))
            return HttpStatus.BAD_REQUEST;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 判断是否为开发模式
     */
    private boolean isDevMode() {
        return "dev".equalsIgnoreCase(activeProfile);
    }
}
