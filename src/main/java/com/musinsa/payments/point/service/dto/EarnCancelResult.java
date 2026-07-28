package com.musinsa.payments.point.service.dto;

public record EarnCancelResult(String pointKey,
                               String canceledEarnPointKey,
                               Long userId,
                               long amount,
                               long balance) {
}
