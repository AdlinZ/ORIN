package com.adlin.orin.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常
 * 用于封装业务逻辑错误
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BusinessException(String message) {
        this(400, message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(int code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public BusinessException(String message, HttpStatus status) {
        this(status.value(), message, status);
    }
}
