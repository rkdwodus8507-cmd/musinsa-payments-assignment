package com.musinsa.payments.point.service;

import com.musinsa.payments.point.config.PointPolicyProperties;
import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.repository.PointPolicyRepository;
import com.musinsa.payments.point.service.dto.PointCommands;
import com.musinsa.payments.point.service.dto.PointResults;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

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
    public PointResults.Policy findPolicy() {
        return toResult(getPolicy());
    }

    @Transactional
    public PointResults.Policy updatePolicy(PointCommands.UpdatePolicy command) {
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

    private PointResults.Policy toResult(PointPolicy policy) {
        return new PointResults.Policy(
                policy.getMinEarnAmount(),
                policy.getMaxEarnAmount(),
                policy.getMaxUserBalance(),
                policy.getDefaultExpireDays(),
                policy.getMinExpireDays(),
                policy.getMaxExpireDays(),
                policy.getUpdatedAt());
    }
}
