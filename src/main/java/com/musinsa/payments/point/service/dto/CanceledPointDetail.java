package com.musinsa.payments.point.service.dto;

import java.time.LocalDateTime;

public record CanceledPointDetail(String earnPointKey,
                                  long amount,
                                  boolean reissued,
                                  String reissuedPointKey,
                                  LocalDateTime expireAt) {
}
