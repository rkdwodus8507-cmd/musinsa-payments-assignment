package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;

public record EarnResult(String pointKey,
                         Long userId,
                         long amount,
                         boolean manual,
                         LocalDateTime expireAt,
                         long balance) {
}
