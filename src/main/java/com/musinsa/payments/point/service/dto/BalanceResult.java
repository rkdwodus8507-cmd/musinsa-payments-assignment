package com.musinsa.payments.point.service.dto;

import java.util.List;
import lombok.Value;

@Value
public class BalanceResult {

    private final Long userId;

    private final long balance;

    private final long manualBalance;

    private final List<EarnedPointSummary> earnedPoints;
}
