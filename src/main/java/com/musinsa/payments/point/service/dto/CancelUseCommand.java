package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class CancelUseCommand {

    String usePointKey;
    long amount;
    String requestKey;
}
