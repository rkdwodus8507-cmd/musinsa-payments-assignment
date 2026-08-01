package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class TransactionResult {

    String pointKey;
    String type;
    long amount;
    String orderId;
    String memo;
    LocalDateTime createdAt;
}
