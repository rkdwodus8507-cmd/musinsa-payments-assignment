package com.musinsa.payments.point.support.error;

public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
