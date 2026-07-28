package com.musinsa.payments.point.service.dto;

public final class PointCommands {

    public record Earn(Long userId, long amount, Integer expireDays, boolean manual, String memo) {

        public static Earn ofUser(Long userId, long amount, Integer expireDays, String memo) {
            return new Earn(userId, amount, expireDays, false, memo);
        }

        public static Earn ofAdmin(Long userId, long amount, Integer expireDays, String memo) {
            return new Earn(userId, amount, expireDays, true, memo);
        }
    }

    public record Use(Long userId, String orderId, long amount) {
    }

    public record UpdatePolicy(long minEarnAmount,
                               long maxEarnAmount,
                               long maxUserBalance,
                               int defaultExpireDays,
                               int minExpireDays,
                               int maxExpireDays) {
    }

    private PointCommands() {
    }
}
