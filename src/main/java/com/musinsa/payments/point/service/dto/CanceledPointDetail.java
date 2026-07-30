package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class CanceledPointDetail {

    private final String earnPointKey;

    private final long amount;

    private final boolean reissued;

    private final String reissuedPointKey;

    private final LocalDateTime expireAt;
}
