package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointAuditRecorder {

    private static final Logger audit = LoggerFactory.getLogger("point-audit");

    private final MeterRegistry meterRegistry;

    public void recordMutation(PointTransaction transaction, long balanceAfter) {
        audit.info("type={} pointKey={} userId={} amount={} orderId={} requestKey={} relatedTransactionId={} balanceAfter={}",
                transaction.getType(),
                transaction.getPointKey(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getOrderId(),
                transaction.getRequestKey(),
                transaction.getRelatedTransactionId(),
                balanceAfter);
        count("point.transactions", transaction.getType());
        meterRegistry.counter("point.amount", "type", transaction.getType().name())
                .increment(transaction.getAmount());
    }

    public void recordDuplicate(PointTransaction alreadyHandled) {
        audit.info("type={} pointKey={} userId={} requestKey={} duplicate=true",
                alreadyHandled.getType(),
                alreadyHandled.getPointKey(),
                alreadyHandled.getUserId(),
                alreadyHandled.getRequestKey());
        count("point.duplicate.requests", alreadyHandled.getType());
    }

    public void recordExpiration(int expiredCount, long expiredAmount) {
        meterRegistry.counter("point.expired.count").increment(expiredCount);
        meterRegistry.counter("point.expired.amount").increment(expiredAmount);
    }

    private void count(String name, PointTransactionType type) {
        meterRegistry.counter(name, "type", type.name()).increment();
    }
}
