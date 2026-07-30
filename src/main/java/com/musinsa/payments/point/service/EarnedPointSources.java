package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.PointUsage;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EarnedPointSources {

    private final Map<Long, EarnedPoint> byId;
    private final Map<Long, String> earnPointKeyById;

    public EarnedPoint of(PointUsage usage) {
        return byId.get(usage.getEarnedPointId());
    }

    public String earnPointKeyOf(PointUsage usage) {
        return earnPointKeyById.get(usage.getEarnedPointId());
    }
}
