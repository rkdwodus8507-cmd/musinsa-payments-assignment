package com.musinsa.payments.point.service.dto;

import java.util.List;
import lombok.Value;

@Value
public class OrderUsageResult {

    private final String orderId;

    private final long usedAmount;

    private final long canceledAmount;

    private final List<OrderUsageDetail> details;
}
