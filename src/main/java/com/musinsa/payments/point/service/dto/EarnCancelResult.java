package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class EarnCancelResult {

    String pointKey;
    String canceledEarnPointKey;
    Long userId;
    long amount;
    long balance;
}
