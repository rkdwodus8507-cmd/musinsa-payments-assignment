package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;
import lombok.Value;

@Value
public class CanceledPointDetail {

    String earnPointKey;
    long amount;
    boolean reissued;
    String reissuedPointKey;
    LocalDateTime expireAt;
}
