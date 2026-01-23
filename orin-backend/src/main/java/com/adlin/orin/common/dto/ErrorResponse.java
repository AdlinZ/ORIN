package com.adlin.orin.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一错误响应DTO
 * 所有API错误都使用此格式返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * 错误代码（业务错误代码）
     */
    private String code;

    /**
     * 用户友好的错误消息
     */
    private String message;

    /**
     * 详细错误信息（开发环境可见，生产环境隐藏）
     */
    private String detail;

    /**
     * 错误发生时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 请求路径
     */
    private String path;

    /**
     * HTTP状态码
     */
    private Integer status;

    /**
     * 额外的错误详情（如字段验证错误）
     */
    private Object metadata;

    /**
     * 请求追踪ID（用于日志关联）
     */
    private String traceId;

    /**
     * 创建成功响应（用于统一响应格式）
     */
    public static ErrorResponse success() {
        return ErrorResponse.builder()
                .code("00000")
                .message("操作成功")
                .status(200)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static ErrorResponse error(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 创建错误响应（带HTTP状态码）
     */
    public static ErrorResponse error(String code, String message, Integer status) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .status(status)
                .build();
    }
}
