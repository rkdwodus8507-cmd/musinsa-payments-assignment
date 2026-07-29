package com.musinsa.payments.point.service.dto;

import java.util.List;

public record UseCancelResult(String pointKey,
                              String canceledUsePointKey,
                              Long userId,
                              String orderId,
                              long amount,
                              long remainingCancelableAmount,
                              long balance,
                              List<CanceledPointDetail> details) {
}
