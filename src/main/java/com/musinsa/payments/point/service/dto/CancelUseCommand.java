package com.musinsa.payments.point.service.dto;

public record CancelUseCommand(String usePointKey, long amount, String requestKey) {
}
