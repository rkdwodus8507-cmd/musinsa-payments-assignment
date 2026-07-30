package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class EarnCommand {

    private final Long userId;

    private final long amount;

    private final Integer expireDays;

    private final boolean manual;

    private final String memo;

    private final String requestKey;

    public static EarnCommand ofUser(Long userId, long amount, Integer expireDays, String memo, String requestKey) {
        return new EarnCommand(userId, amount, expireDays, false, memo, requestKey);
    }

    public static EarnCommand ofAdmin(Long userId, long amount, Integer expireDays, String memo, String requestKey) {
        return new EarnCommand(userId, amount, expireDays, true, memo, requestKey);
    }
}
