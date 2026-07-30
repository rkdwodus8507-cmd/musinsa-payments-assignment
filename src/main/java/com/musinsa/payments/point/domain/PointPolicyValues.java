package com.musinsa.payments.point.domain;

public record PointPolicyValues(long minEarnAmount,
                                long maxEarnAmount,
                                long maxUserBalance,
                                int defaultExpireDays,
                                int minExpireDays,
                                int maxExpireDays) {
}
