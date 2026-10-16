package com.bookingcore.common.exception;

/**
 * Base class for expected, client-facing failures. The {@link ErrorCode} determines the HTTP status and title;
 * the message becomes the Problem Details {@code detail} and must therefore be safe to show to API clients.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
