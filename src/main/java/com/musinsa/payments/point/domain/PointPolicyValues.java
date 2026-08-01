package com.musinsa.payments.point.domain;

import lombok.Value;

@Value
public class PointPolicyValues {

    long minEarnAmount;
    long maxEarnAmount;
    long maxUserBalance;
    int defaultExpireDays;
    int minExpireDays;
    int maxExpireDays;
}
