package com.musinsa.payments.point.config;

import com.musinsa.payments.point.domain.PointPolicyValues;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "point.policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PointPolicyProperties {

    private long minEarnAmount;

    private long maxEarnAmount;

    private long maxUserBalance;

    private int defaultExpireDays;

    private int minExpireDays;

    private int maxExpireDays;

    public PointPolicyValues toValues() {
        return new PointPolicyValues(
                minEarnAmount, maxEarnAmount, maxUserBalance, defaultExpireDays, minExpireDays, maxExpireDays);
    }
}
