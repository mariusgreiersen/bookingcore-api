package com.bookingcore.common.exception;

import java.util.UUID;

public class NotFoundException extends BusinessException {

    private NotFoundException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public static NotFoundException resource(UUID id) {
        return new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "No resource exists with id " + id + ".");
    }

    public static NotFoundException reservation(UUID id) {
        return new NotFoundException(ErrorCode.RESERVATION_NOT_FOUND, "No reservation exists with id " + id + ".");
    }
}
