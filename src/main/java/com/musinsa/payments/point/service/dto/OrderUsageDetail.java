package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class OrderUsageDetail {

    String usePointKey;
    String earnPointKey;
    long amount;
    long canceledAmount;
    boolean manual;
    LocalDateTime earnExpireAt;
}
