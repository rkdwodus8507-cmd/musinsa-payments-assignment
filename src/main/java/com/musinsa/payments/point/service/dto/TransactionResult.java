package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class TransactionResult {

    private final String pointKey;

    private final String type;

    private final long amount;

    private final String orderId;

    private final String memo;

    private final LocalDateTime createdAt;
}
