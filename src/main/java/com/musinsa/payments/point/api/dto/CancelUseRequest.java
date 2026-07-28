package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.Positive;

public record CancelUseRequest(
        @Positive long amount
) {
}
