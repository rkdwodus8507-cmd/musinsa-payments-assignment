package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class ExpirationResult {

    int expiredCount;
    long expiredAmount;
}
