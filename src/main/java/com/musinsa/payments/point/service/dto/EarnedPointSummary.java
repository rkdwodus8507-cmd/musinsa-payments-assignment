package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class EarnedPointSummary {

    String earnPointKey;
    long originalAmount;
    long remainingAmount;
    boolean manual;
    String status;
    LocalDateTime expireAt;
}
