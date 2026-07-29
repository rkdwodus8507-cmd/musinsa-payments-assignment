package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.Size;

public record CancelEarnRequest(
        @Size(max = 64) String requestKey
) {
}
