package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;

public record TransactionResult(String pointKey,
                                String type,
                                long amount,
                                String orderId,
                                String memo,
                                LocalDateTime createdAt) {
}
