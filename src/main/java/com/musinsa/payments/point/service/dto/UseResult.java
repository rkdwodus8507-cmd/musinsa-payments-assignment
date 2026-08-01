package com.musinsa.payments.point.service.dto;

import java.util.List;
import lombok.Value;

@Value
public class UseResult {

    String pointKey;
    Long userId;
    String orderId;
    long amount;
    long balance;
    List<UsedPointDetail> details;
}
