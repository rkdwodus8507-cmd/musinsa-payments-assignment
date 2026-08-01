package com.musinsa.payments.point.service.dto;

import lombok.Value;

@Value
public class CancelEarnCommand {

    String earnPointKey;
    String requestKey;
}
