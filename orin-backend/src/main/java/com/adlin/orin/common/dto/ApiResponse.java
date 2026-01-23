package com.adlin.orin.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应包装类
 * 用于包装所有成功的API响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 响应代码
     */
    @Builder.Default
    private String code = "00000";

    /**
     * 响应消息
     */
    @Builder.Default
    private String message = "操作成功";

    /**
     * 响应数据
     */
    private T data;

    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code("00000")
                .message("操作成功")
                .data(data)
                .build();
    }

    /**
     * 创建成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code("00000")
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 创建成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .code("00000")
                .message("操作成功")
                .build();
    }
}
