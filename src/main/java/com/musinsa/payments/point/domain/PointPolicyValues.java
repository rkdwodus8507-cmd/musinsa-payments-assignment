package com.musinsa.payments.point.domain;

import lombok.Value;

@Value
public class PointPolicyValues {

    private final long minEarnAmount;

    private final long maxEarnAmount;

    private final long maxUserBalance;

    private final int defaultExpireDays;

    private final int minExpireDays;

    private final int maxExpireDays;
}
