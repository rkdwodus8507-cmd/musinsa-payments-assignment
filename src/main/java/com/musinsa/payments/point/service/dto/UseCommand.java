package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class UseCommand {

    private final Long userId;

    private final String orderId;

    private final long amount;

    private final String requestKey;
}
