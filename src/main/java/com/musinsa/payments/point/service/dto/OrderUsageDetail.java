package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;

public record OrderUsageDetail(String usePointKey,
                               String earnPointKey,
                               long amount,
                               long canceledAmount,
                               boolean manual,
                               LocalDateTime earnExpireAt) {
}
