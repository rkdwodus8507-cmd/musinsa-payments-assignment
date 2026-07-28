package com.musinsa.payments.point.service.dto;

import java.util.List;

public record OrderUsageResult(String orderId,
                               long usedAmount,
                               long canceledAmount,
                               List<OrderUsageDetail> details) {
}
