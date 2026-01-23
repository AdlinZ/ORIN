package com.adlin.orin.common.exception;

/**
 * 授权异常
 * 当用户权限不足时抛出
 */
public class AuthorizationException extends BusinessException {

    public AuthorizationException(String message) {
        super(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, message);
    }

    public AuthorizationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
