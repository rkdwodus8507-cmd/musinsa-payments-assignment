package com.musinsa.payments.point.service.dto;

public record EarnCommand(Long userId, long amount, Integer expireDays, boolean manual, String memo) {

    public static EarnCommand ofUser(Long userId, long amount, Integer expireDays, String memo) {
        return new EarnCommand(userId, amount, expireDays, false, memo);
    }

    public static EarnCommand ofAdmin(Long userId, long amount, Integer expireDays, String memo) {
        return new EarnCommand(userId, amount, expireDays, true, memo);
    }
}
