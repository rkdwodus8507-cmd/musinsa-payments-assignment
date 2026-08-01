package com.musinsa.payments.point.support.error;

import lombok.Value;

@Value
public class ErrorResponse {

    String code;
    String message;
    String requestId;
}
