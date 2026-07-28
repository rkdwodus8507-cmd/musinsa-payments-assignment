package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class PointResults {

    public record Earn(String pointKey,
                       Long userId,
                       long amount,
                       boolean manual,
                       LocalDateTime expireAt,
                       long balance) {
    }

    public record EarnCancel(String pointKey,
                             String canceledEarnPointKey,
                             Long userId,
                             long amount,
                             long balance) {
    }

    public record Use(String pointKey,
                      Long userId,
                      String orderId,
                      long amount,
                      long balance,
                      List<UsedLot> details) {
    }

    public record UsedLot(String earnPointKey,
                          long amount,
                          boolean manual,
                          LocalDateTime expireAt) {
    }

    public record UseCancel(String pointKey,
                            String canceledUsePointKey,
                            Long userId,
                            String orderId,
                            long amount,
                            long remainingCancelableAmount,
                            long balance,
                            List<CanceledLot> details) {
    }

    public record CanceledLot(String earnPointKey,
                              long amount,
                              boolean reissued,
                              String reissuedPointKey,
                              LocalDateTime expireAt) {
    }

    public record Balance(Long userId,
                          long balance,
                          long manualBalance,
                          List<Lot> lots) {
    }

    public record Lot(String earnPointKey,
                      long originalAmount,
                      long remainingAmount,
                      boolean manual,
                      String status,
                      LocalDateTime expireAt) {
    }

    public record Transaction(String pointKey,
                              String type,
                              long amount,
                              String orderId,
                              String memo,
                              LocalDateTime createdAt) {
    }

    public record OrderUsage(String orderId,
                             long usedAmount,
                             long canceledAmount,
                             List<OrderUsageDetail> details) {
    }

    public record OrderUsageDetail(String usePointKey,
                                   String earnPointKey,
                                   long amount,
                                   long canceledAmount,
                                   boolean manual,
                                   LocalDateTime earnExpireAt) {
    }

    public record Policy(long minEarnAmount,
                         long maxEarnAmount,
                         long maxUserBalance,
                         int defaultExpireDays,
                         int minExpireDays,
                         int maxExpireDays,
                         LocalDateTime updatedAt) {
    }

    public record Expiration(int expiredLotCount, long expiredAmount) {
    }

    private PointResults() {
    }
}
