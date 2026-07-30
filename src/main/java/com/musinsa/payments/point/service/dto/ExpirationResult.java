package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class ExpirationResult {

    private final int expiredCount;

    private final long expiredAmount;
}
