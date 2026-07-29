package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EarnedPointReader {

    private final EarnedPointRepository earnedPointRepository;
    private final PointTransactionRepository transactionRepository;
    private final Clock clock;

    public long balanceOf(Long userId) {
        return earnedPointRepository.sumAvailableAmount(userId, EarnedPointStatus.AVAILABLE, LocalDateTime.now(clock));
    }

    public List<EarnedPoint> usableInPriorityOrder(Long userId) {
        return earnedPointRepository.findUsableInPriorityOrder(
                userId, EarnedPointStatus.AVAILABLE, LocalDateTime.now(clock));
    }

    public List<EarnedPoint> allOf(Long userId) {
        return earnedPointRepository.findByUserIdOrderByIdAsc(userId);
    }

    public EarnedPoint byTransaction(Long transactionId) {
        return earnedPointRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> PointException.of(ErrorCode.EARNED_POINT_NOT_FOUND, "transactionId=" + transactionId));
    }

    public Map<Long, EarnedPoint> byIds(Collection<Long> earnedPointIds) {
        return earnedPointRepository.findAllById(earnedPointIds).stream()
                .collect(Collectors.toMap(EarnedPoint::getId, Function.identity()));
    }

    public Map<Long, String> earnPointKeyByEarnedPointId(Collection<EarnedPoint> earnedPoints) {
        List<Long> transactionIds = earnedPoints.stream()
                .map(EarnedPoint::getTransactionId)
                .distinct()
                .toList();
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> keysByTransaction = transactionRepository.findByIdIn(transactionIds).stream()
                .collect(Collectors.toMap(PointTransaction::getId, PointTransaction::getPointKey));
        return earnedPoints.stream()
                .collect(Collectors.toMap(EarnedPoint::getId, it -> keysByTransaction.get(it.getTransactionId())));
    }
}
