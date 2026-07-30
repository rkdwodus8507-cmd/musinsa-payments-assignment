package com.musinsa.payments.point.service.dto;

import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.domain.PointPolicyValues;
import java.time.LocalDateTime;

public record PolicyResult(long minEarnAmount,
                           long maxEarnAmount,
                           long maxUserBalance,
                           int defaultExpireDays,
                           int minExpireDays,
                           int maxExpireDays,
                           LocalDateTime updatedAt) {

    public static PolicyResult of(PointPolicy policy) {
        PointPolicyValues values = policy.values();
        return new PolicyResult(
                values.minEarnAmount(),
                values.maxEarnAmount(),
                values.maxUserBalance(),
                values.defaultExpireDays(),
                values.minExpireDays(),
                values.maxExpireDays(),
                policy.getUpdatedAt());
    }
}
