package com.musinsa.payments.point.config;

import com.musinsa.payments.point.domain.PointPolicyValues;
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

    public PointPolicyValues toValues() {
        return new PointPolicyValues(
                minEarnAmount, maxEarnAmount, maxUserBalance, defaultExpireDays, minExpireDays, maxExpireDays);
    }
}
