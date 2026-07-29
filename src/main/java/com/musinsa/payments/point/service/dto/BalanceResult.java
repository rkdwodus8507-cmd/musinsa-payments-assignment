package com.musinsa.payments.point.service.dto;

import java.util.List;

public record BalanceResult(Long userId,
                            long balance,
                            long manualBalance,
                            List<EarnedPointSummary> lots) {
}
