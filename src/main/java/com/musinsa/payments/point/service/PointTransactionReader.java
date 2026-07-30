package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.support.IdChunks;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    public Map<Long, String> pointKeysByTransactionId(Collection<Long> transactionIds) {
        List<Long> ids = transactionIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return IdChunks.split(ids).stream()
                .flatMap(chunk -> transactionRepository.findByIdIn(chunk).stream())
                .collect(Collectors.toMap(PointTransaction::getId, PointTransaction::getPointKey));
    }

    public Map<Long, String> earnPointKeysByEarnedPointId(Collection<EarnedPoint> earnedPoints) {
        Map<Long, String> byTransaction = pointKeysByTransactionId(
                earnedPoints.stream().map(EarnedPoint::getTransactionId).toList());
        return earnedPoints.stream()
                .collect(Collectors.toMap(EarnedPoint::getId, it -> byTransaction.get(it.getTransactionId())));
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
