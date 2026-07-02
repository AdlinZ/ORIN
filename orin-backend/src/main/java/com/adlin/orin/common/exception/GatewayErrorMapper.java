package com.adlin.orin.common.exception;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Gateway MVP audit 错误码映射助手 —— 纯函数工具类。
 *
 * <p>目标是把 UnifiedGatewayProxyFilter / UnifiedGatewayApiController
 * 这些网关侧自由文本的错误来源，转换为结构化的 ErrorCode.getCode() 字符串，
 * 写入 audit_logs.error_code / gateway_audit_logs.error_code。
 *
 * <p>三个入口：
 * <ul>
 *   <li>{@link #fromAuditReason(String)} —— 由已知 errorMessage 自由文本反查错码</li>
 *   <li>{@link #fromHttpStatus(int)} —— 由 HTTP 状态码映射</li>
 *   <li>{@link #fromThrowable(Throwable)} —— 由抛出的异常类型映射</li>
 * </ul>
 *
 * <p>所有入口允许返回 {@code null} —— 表示当前数据无法归类，仍可与 errorMessage 自由文本并存。
 */
public final class GatewayErrorMapper {

    private GatewayErrorMapper() {
    }

    /**
     * 从已知 free-text errorMessage 反查错码。
     * 用于 UnifiedGatewayProxyFilter 八个 saveAuditLog(...) 调用点（都是已知的固定文案）。
     */
    public static String fromAuditReason(String freeText) {
        if (freeText == null || freeText.isBlank()) {
            return null;
        }
        String t = freeText.toLowerCase();
        if (t.contains("circuit")) {
            return ErrorCode.TASK_RETRY_EXHAUSTED.getCode();
        }
        if (t.contains("rate limit")) {
            return ErrorCode.API_KEY_RATE_LIMITED.getCode();
        }
        if (t.contains("authentication") || t.contains("api key")) {
            return ErrorCode.AUTH_API_KEY_INVALID.getCode();
        }
        if (t.contains("unsupported") || t.contains("too large")) {
            return ErrorCode.INVALID_PARAMETER.getCode();
        }
        if (t.contains("upstream")) {
            return ErrorCode.MODEL_API_ERROR.getCode();
        }
        return null;
    }

    /**
     * 从 HTTP 状态码映射。
     * 仅覆盖 Gateway MVP 关心的分支；未知 status 返回 null 或 SYSTEM_ERROR。
     */
    public static String fromHttpStatus(int status) {
        switch (status) {
            case 400:
            case 405:
            case 413:
                return ErrorCode.INVALID_PARAMETER.getCode();
            case 401:
                return ErrorCode.AUTH_API_KEY_INVALID.getCode();
            case 403:
                return ErrorCode.FORBIDDEN.getCode();
            case 429:
                return ErrorCode.API_KEY_RATE_LIMITED.getCode();
            case 502:
                return ErrorCode.MODEL_API_ERROR.getCode();
            case 503:
                return ErrorCode.RESOURCE_NOT_FOUND.getCode();
            case 504:
                return ErrorCode.TASK_TIMEOUT.getCode();
            default:
                return status >= 500 ? ErrorCode.SYSTEM_ERROR.getCode() : null;
        }
    }

    /**
     * 从 Throwable 提取错误码：BusinessException 自带 ErrorCode；否则降级。
     * 用于 controller 的 .onErrorResume(...) 路径。
     */
    public static String fromThrowable(Throwable t) {
        if (t == null) {
            return null;
        }
        if (t instanceof BusinessException be) {
            return be.getErrorCode().getCode();
        }
        if (t instanceof TimeoutException) {
            return ErrorCode.TASK_TIMEOUT.getCode();
        }
        if (t instanceof IOException) {
            return ErrorCode.MODEL_API_ERROR.getCode();
        }
        return ErrorCode.SYSTEM_ERROR.getCode();
    }
}
