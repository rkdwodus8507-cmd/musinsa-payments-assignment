package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
import com.musinsa.payments.point.service.dto.ExpirationResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PointAuditRecorder {

    private static final Logger audit = LoggerFactory.getLogger("point-audit");

    private final MeterRegistry meterRegistry;
    private final AtomicLong expirationBacklog = new AtomicLong();

    public PointAuditRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("point.expiration.backlog", expirationBacklog);
    }

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

    public void recordExpiration(ExpirationResult expired, LocalDateTime baseTime, long elapsedNanos) {
        if (expired.getExpiredCount() > 0) {
            audit.info("type=EXPIRE count={} amount={} baseTime={}",
                    expired.getExpiredCount(), expired.getExpiredAmount(), baseTime);
        }
        meterRegistry.counter("point.expired.count").increment(expired.getExpiredCount());
        meterRegistry.counter("point.expired.amount").increment(expired.getExpiredAmount());
        meterRegistry.timer("point.expiration.duration").record(elapsedNanos, TimeUnit.NANOSECONDS);
        expirationBacklog.addAndGet(-expired.getExpiredCount());
    }

    public void recordExpirationBacklog(long expirablePoints) {
        expirationBacklog.set(expirablePoints);
    }

    private void count(String name, PointTransactionType type) {
        meterRegistry.counter(name, "type", type.name()).increment();
    }
}
