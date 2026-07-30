package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;

public record PolicyResult(long minEarnAmount,
                           long maxEarnAmount,
                           long maxUserBalance,
                           int defaultExpireDays,
                           int minExpireDays,
                           int maxExpireDays,
                           LocalDateTime updatedAt) {
}
