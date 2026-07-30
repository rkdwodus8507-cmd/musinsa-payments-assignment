package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
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
        return byPointKey(pointKey, PointTransactionType.EARN, ErrorCode.NOT_EARN_TRANSACTION);
    }

    public PointTransaction useByPointKey(String pointKey) {
        return byPointKey(pointKey, PointTransactionType.USE, ErrorCode.NOT_USE_TRANSACTION);
    }

    private PointTransaction byPointKey(String pointKey, PointTransactionType expected, ErrorCode whenMismatched) {
        PointTransaction transaction = transactionRepository.findByPointKey(pointKey)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "pointKey=" + pointKey));
        if (transaction.getType() != expected) {
            throw PointException.of(whenMismatched, "type=" + transaction.getType());
        }
        return transaction;
    }
}
