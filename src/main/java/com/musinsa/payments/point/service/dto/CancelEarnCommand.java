package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class CancelEarnCommand {

    private final String earnPointKey;

    private final String requestKey;
}
