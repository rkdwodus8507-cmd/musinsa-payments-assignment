package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record UpdatePolicyRequest(
        @Positive long minEarnAmount,
        @Positive long maxEarnAmount,
        @Positive long maxUserBalance,
        @Min(1) int defaultExpireDays,
        @Min(1) int minExpireDays,
        @Min(1) int maxExpireDays
) {
}
