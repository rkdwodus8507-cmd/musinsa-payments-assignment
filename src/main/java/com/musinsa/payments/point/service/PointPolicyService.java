package com.musinsa.payments.point.service;

import com.musinsa.payments.point.config.PointPolicyProperties;
import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.repository.PointPolicyRepository;
import com.musinsa.payments.point.service.dto.PolicyResult;
import com.musinsa.payments.point.service.dto.UpdatePolicyCommand;
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
        return toResult(getPolicy());
    }

    @Transactional
    public PolicyResult updatePolicy(UpdatePolicyCommand command) {
        PointPolicy policy = getPolicy();
        policy.update(
                command.minEarnAmount(),
                command.maxEarnAmount(),
                command.maxUserBalance(),
                command.defaultExpireDays(),
                command.minExpireDays(),
                command.maxExpireDays(),
                LocalDateTime.now(clock));
        return toResult(policy);
    }

    @Transactional
    public void initializeIfAbsent() {
        if (policyRepository.existsById(PointPolicy.SINGLETON_ID)) {
            return;
        }
        policyRepository.save(PointPolicy.create(
                policyProperties.minEarnAmount(),
                policyProperties.maxEarnAmount(),
                policyProperties.maxUserBalance(),
                policyProperties.defaultExpireDays(),
                policyProperties.minExpireDays(),
                policyProperties.maxExpireDays(),
                LocalDateTime.now(clock)));
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
