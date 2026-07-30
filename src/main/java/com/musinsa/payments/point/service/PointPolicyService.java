package com.musinsa.payments.point.service;

import com.musinsa.payments.point.config.PointPolicyProperties;
import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.domain.PointPolicyValues;
import com.musinsa.payments.point.repository.PointPolicyRepository;
import com.musinsa.payments.point.service.dto.PolicyResult;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointPolicyService {

    private final PointPolicyRepository policyRepository;
    private final PointPolicyProperties policyProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PointPolicy getPolicy() {
        return policyRepository.findById(PointPolicy.SINGLETON_ID)
                .orElseThrow(() -> PointException.of(ErrorCode.POLICY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PolicyResult findPolicy() {
        return PolicyResult.of(getPolicy());
    }

    @Transactional
    public PolicyResult updatePolicy(PointPolicyValues values) {
        PointPolicy policy = getPolicy();
        policy.update(values, LocalDateTime.now(clock));
        return PolicyResult.of(policy);
    }

    @Transactional
    public void initializeIfAbsent() {
        if (policyRepository.existsById(PointPolicy.SINGLETON_ID)) {
            return;
        }
        policyRepository.save(PointPolicy.create(policyProperties.toValues(), LocalDateTime.now(clock)));
    }
}
