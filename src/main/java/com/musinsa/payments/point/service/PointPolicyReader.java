package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.repository.PointPolicyRepository;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointPolicyReader {

    private final PointPolicyRepository policyRepository;

    public PointPolicy current() {
        return policyRepository.findById(PointPolicy.SINGLETON_ID)
                .orElseThrow(() -> PointException.of(ErrorCode.POLICY_NOT_FOUND));
    }
}
