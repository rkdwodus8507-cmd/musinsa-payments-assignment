package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class EarnResult {

    String pointKey;
    Long userId;
    long amount;
    boolean manual;
    LocalDateTime expireAt;
    long balance;
}
