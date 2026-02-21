package com.adlin.orin.common.dto;

import com.adlin.orin.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一响应结果类
 * 用于标准化所有 API 的返回格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /**
     * 业务状态码
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 错误详情 (仅在开发环境或特定错误下返回)
     */
    private String detail;

    /**
     * 响应时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 请求路径
     */
    private String path;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 附加元数据 (如分页信息或验证错误列表)
     */
    private Object metadata;

    /**
     * 构造成功响应
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 构造带数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    /**
     * 构造带数据和自定义消息的成功响应
     */
    public static <T> Result<T> success(T data, String message) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 构造错误响应
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 构造自定义消息的错误响应
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return error(errorCode.getCode(), message);
    }

    /**
     * 构造全自定义的错误响应
     */
    public static <T> Result<T> error(String code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
