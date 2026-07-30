package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class EarnResult {

    private final String pointKey;

    private final Long userId;

    private final long amount;

    private final boolean manual;

    private final LocalDateTime expireAt;

    private final long balance;
}
