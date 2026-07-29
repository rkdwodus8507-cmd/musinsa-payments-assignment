package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;

public record UsedPointDetail(String earnPointKey,
                              long amount,
                              boolean manual,
                              LocalDateTime expireAt) {
}
