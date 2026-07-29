package com.musinsa.payments.point.service.dto;

import java.util.List;

public record UseResult(String pointKey,
                        Long userId,
                        String orderId,
                        long amount,
                        long balance,
                        List<UsedPointDetail> details) {
}
