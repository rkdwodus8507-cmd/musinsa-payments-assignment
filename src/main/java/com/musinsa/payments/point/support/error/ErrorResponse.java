package com.musinsa.payments.point.support.error;

import lombok.Value;

@Value
public class ErrorResponse {

    private final String code;

    private final String message;

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
