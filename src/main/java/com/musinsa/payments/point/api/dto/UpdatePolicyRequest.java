package com.musinsa.payments.point.api.dto;

import com.musinsa.payments.point.domain.PointPolicyValues;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePolicyRequest {

    @Positive
    private long minEarnAmount;

    @Positive
    private long maxEarnAmount;

    @Positive
    private long maxUserBalance;

    @Min(1)
    private int defaultExpireDays;

    @Min(1)
    private int minExpireDays;

    @Min(1)
    private int maxExpireDays;

    public PointPolicyValues toValues() {
        return new PointPolicyValues(
                minEarnAmount, maxEarnAmount, maxUserBalance, defaultExpireDays, minExpireDays, maxExpireDays);
    }
}
