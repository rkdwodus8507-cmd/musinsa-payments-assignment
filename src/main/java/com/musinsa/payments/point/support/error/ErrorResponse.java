package com.musinsa.payments.point.support.error;

import lombok.Value;

@Value
public class ErrorResponse {

    private final String code;

    private final String message;

    private final String requestId;
}
