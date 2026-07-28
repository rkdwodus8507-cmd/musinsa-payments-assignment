package com.musinsa.payments.point.service.dto;

public record UseCommand(Long userId, String orderId, long amount) {
}
