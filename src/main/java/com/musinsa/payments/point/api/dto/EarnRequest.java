package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EarnRequest(
        @NotNull Long userId,
        @Positive long amount,
        @Min(1) Integer expireDays,
        @Size(max = 255) String memo
) {
}
