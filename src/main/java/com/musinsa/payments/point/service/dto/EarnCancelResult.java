package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class EarnCancelResult {

    private final String pointKey;

    private final String canceledEarnPointKey;

    private final Long userId;

    private final long amount;

    private final long balance;
}
