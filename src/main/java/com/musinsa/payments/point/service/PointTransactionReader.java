package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointTransactionReader {

    private final PointTransactionRepository transactionRepository;

    public PointTransaction byId(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "transactionId=" + transactionId));
    }

    public PointTransaction earnByPointKey(String pointKey) {
        PointTransaction transaction = byPointKey(pointKey);
        if (!transaction.isEarn()) {
            throw PointException.of(ErrorCode.NOT_EARN_TRANSACTION, "type=" + transaction.getType());
        }
        return transaction;
    }

    public PointTransaction useByPointKey(String pointKey) {
        PointTransaction transaction = byPointKey(pointKey);
        if (!transaction.isUse()) {
            throw PointException.of(ErrorCode.NOT_USE_TRANSACTION, "type=" + transaction.getType());
        }
        return transaction;
    }

    private PointTransaction byPointKey(String pointKey) {
        return transactionRepository.findByPointKey(pointKey)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "pointKey=" + pointKey));
    }
}
