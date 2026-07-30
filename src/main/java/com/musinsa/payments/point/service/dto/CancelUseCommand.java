package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class CancelUseCommand {

    private final String usePointKey;

    private final long amount;

    private final String requestKey;
}
