package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class PolicyResult {

    private final long minEarnAmount;

    private final long maxEarnAmount;

    private final long maxUserBalance;

    private final int defaultExpireDays;

    private final int minExpireDays;

    private final int maxExpireDays;

    private final LocalDateTime updatedAt;
}
