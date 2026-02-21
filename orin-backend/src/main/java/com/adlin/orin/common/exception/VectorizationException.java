package com.adlin.orin.common.exception;

/**
 * 向量化异常
 */
public class VectorizationException extends BusinessException {
    public VectorizationException(String message) {
        super(ErrorCode.VECTORIZATION_FAILED, message);
    }

    public VectorizationException(String message, Throwable cause) {
        super(ErrorCode.VECTORIZATION_FAILED, message, cause);
    }
}
