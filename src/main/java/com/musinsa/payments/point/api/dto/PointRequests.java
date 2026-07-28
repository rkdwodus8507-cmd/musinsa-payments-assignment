package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class PointRequests {

    public record Earn(
            @NotNull Long userId,
            @Positive long amount,
            @Min(1) Integer expireDays,
            @Size(max = 255) String memo
    ) {
    }

    public record Use(
            @NotNull Long userId,
            @NotBlank @Size(max = 64) String orderId,
            @Positive long amount
    ) {
    }

    public record CancelUse(
            @Positive long amount
    ) {
    }

    public record UpdatePolicy(
            @Positive long minEarnAmount,
            @Positive long maxEarnAmount,
            @Positive long maxUserBalance,
            @Min(1) int defaultExpireDays,
            @Min(1) int minExpireDays,
            @Min(1) int maxExpireDays
    ) {
    }

    private PointRequests() {
    }
}
