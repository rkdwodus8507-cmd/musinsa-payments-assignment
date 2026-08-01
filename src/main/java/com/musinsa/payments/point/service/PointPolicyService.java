package com.musinsa.payments.point.service;

import com.musinsa.payments.point.config.PointPolicyProperties;
import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.domain.PointPolicyValues;
import com.musinsa.payments.point.repository.PointPolicyRepository;
import com.musinsa.payments.point.service.dto.PolicyResult;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointPolicyService {

    private final PointPolicyRepository policyRepository;
    private final PointPolicyReader policyReader;
    private final PointPolicyProperties policyProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PolicyResult findPolicy() {
        return toResult(policyReader.current());
    }

    @Transactional
    public PolicyResult updatePolicy(PointPolicyValues values) {
        PointPolicy policy = policyReader.current();
        policy.update(values, LocalDateTime.now(clock));
        return toResult(policy);
    }

    @Transactional
    public void initializeIfAbsent() {
        if (policyRepository.existsById(PointPolicy.SINGLETON_ID)) {
            return;
        }
        policyRepository.save(PointPolicy.create(policyProperties.toValues(), LocalDateTime.now(clock)));
    }

    private PolicyResult toResult(PointPolicy policy) {
        return new PolicyResult(
                policy.getMinEarnAmount(),
                policy.getMaxEarnAmount(),
                policy.getMaxUserBalance(),
                policy.getDefaultExpireDays(),
                policy.getMinExpireDays(),
                policy.getMaxExpireDays(),
                policy.getUpdatedAt());
    }
}
