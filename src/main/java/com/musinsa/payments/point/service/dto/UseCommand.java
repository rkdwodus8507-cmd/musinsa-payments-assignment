package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class UseCommand {

    Long userId;
    String orderId;
    long amount;
    String requestKey;
}
