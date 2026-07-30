package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PointAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("point-audit");

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
    }

    public void recordDuplicate(PointTransaction alreadyHandled) {
        audit.info("type={} pointKey={} userId={} requestKey={} duplicate=true",
                alreadyHandled.getType(),
                alreadyHandled.getPointKey(),
                alreadyHandled.getUserId(),
                alreadyHandled.getRequestKey());
    }
}
