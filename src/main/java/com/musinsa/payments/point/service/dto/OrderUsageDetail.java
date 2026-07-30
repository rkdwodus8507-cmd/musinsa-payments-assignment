package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class OrderUsageDetail {

    private final String usePointKey;

    private final String earnPointKey;

    private final long amount;

    private final long canceledAmount;

    private final boolean manual;

    private final LocalDateTime earnExpireAt;
}
