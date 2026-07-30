package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class EarnedPointSummary {

    private final String earnPointKey;

    private final long originalAmount;

    private final long remainingAmount;

    private final boolean manual;

    private final String status;

    private final LocalDateTime expireAt;
}
