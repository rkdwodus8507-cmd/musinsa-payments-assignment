package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UseRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 64) String orderId,
        @Positive long amount,
        @Size(max = 64) String requestKey
) {
}
