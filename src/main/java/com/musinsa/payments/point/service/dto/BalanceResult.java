package com.musinsa.payments.point.service.dto;

import java.util.List;
import lombok.Value;

@Value
public class BalanceResult {

    Long userId;
    long balance;
    long manualBalance;
    List<EarnedPointSummary> earnedPoints;
}
