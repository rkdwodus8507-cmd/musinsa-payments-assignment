package com.musinsa.payments.point.service.dto;

import java.util.List;
import lombok.Value;

@Value
public class UseCancelResult {

    private final String pointKey;

    private final String canceledUsePointKey;

    private final Long userId;

    private final String orderId;

    private final long amount;

    private final long remainingCancelableAmount;

    private final long balance;

    private final List<CanceledPointDetail> details;
}
