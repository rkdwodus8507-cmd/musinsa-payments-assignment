package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class UsedPointDetail {

    private final String earnPointKey;

    private final long amount;

    private final boolean manual;

    private final LocalDateTime expireAt;
}
