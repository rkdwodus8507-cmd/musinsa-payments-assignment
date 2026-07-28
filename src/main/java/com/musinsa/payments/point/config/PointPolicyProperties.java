package com.musinsa.payments.point.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "point.policy")
public record PointPolicyProperties(
        long minEarnAmount,
        long maxEarnAmount,
        long maxUserBalance,
        int defaultExpireDays,
        int minExpireDays,
        int maxExpireDays
) {
}
