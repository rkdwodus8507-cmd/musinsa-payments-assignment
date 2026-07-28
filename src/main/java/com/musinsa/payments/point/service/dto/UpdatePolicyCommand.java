package com.musinsa.payments.point.service.dto;

public record UpdatePolicyCommand(long minEarnAmount,
                                  long maxEarnAmount,
                                  long maxUserBalance,
                                  int defaultExpireDays,
                                  int minExpireDays,
                                  int maxExpireDays) {
}
