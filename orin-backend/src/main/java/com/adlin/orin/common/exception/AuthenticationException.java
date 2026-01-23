package com.adlin.orin.common.exception;

/**
 * 认证异常
 * 当用户认证失败时抛出
 */
public class AuthenticationException extends BusinessException {

    public AuthenticationException(String message) {
        super(ErrorCode.AUTH_INVALID_CREDENTIALS, message);
    }

    public AuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
