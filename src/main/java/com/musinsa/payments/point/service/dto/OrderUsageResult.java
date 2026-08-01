package com.musinsa.payments.point.service.dto;

import java.util.List;
import lombok.Value;

@Value
public class OrderUsageResult {

    String orderId;
    long usedAmount;
    long canceledAmount;
    List<OrderUsageDetail> details;
}
