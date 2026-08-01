package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class UsedPointDetail {

    String earnPointKey;
    long amount;
    boolean manual;
    LocalDateTime expireAt;
}
