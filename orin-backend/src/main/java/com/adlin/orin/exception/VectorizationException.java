package com.adlin.orin.exception;

public class VectorizationException extends RuntimeException {
    public VectorizationException(String message) {
        super(message);
    }

    public VectorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
