package com.musinsa.payments.point.service.dto;

public record EarnCommand(Long userId, long amount, Integer expireDays, boolean manual, String memo, String requestKey) {

    public static EarnCommand ofUser(Long userId, long amount, Integer expireDays, String memo, String requestKey) {
        return new EarnCommand(userId, amount, expireDays, false, memo, requestKey);
    }

    public static EarnCommand ofAdmin(Long userId, long amount, Integer expireDays, String memo, String requestKey) {
        return new EarnCommand(userId, amount, expireDays, true, memo, requestKey);
    }
}
