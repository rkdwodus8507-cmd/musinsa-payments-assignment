package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;

public record LotBalance(String earnPointKey,
                         long originalAmount,
                         long remainingAmount,
                         boolean manual,
                         String status,
                         LocalDateTime expireAt) {
}
