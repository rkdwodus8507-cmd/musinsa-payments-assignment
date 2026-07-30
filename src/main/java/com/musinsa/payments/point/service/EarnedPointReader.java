package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.support.IdChunks;
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
    private final Clock clock;

    public long balanceOf(Long userId) {
        return earnedPointRepository.sumAvailableAmount(userId, EarnedPointStatus.AVAILABLE, now());
    }

    public List<EarnedPoint> usableInPriorityOrder(Long userId) {
        return earnedPointRepository.findUsableInPriorityOrder(userId, EarnedPointStatus.AVAILABLE, now());
    }

    public List<EarnedPoint> allOf(Long userId) {
        return earnedPointRepository.findByUserIdOrderByIdAsc(userId);
    }


    public Map<Long, EarnedPoint> byIds(Collection<Long> earnedPointIds) {
        Map<Long, EarnedPoint> found = IdChunks.split(earnedPointIds).stream()
                .flatMap(chunk -> earnedPointRepository.findAllById(chunk).stream())
                .collect(Collectors.toMap(EarnedPoint::getId, Function.identity()));
        if (found.size() != earnedPointIds.size()) {
            throw PointException.of(ErrorCode.EARNED_POINT_NOT_FOUND, "ids=" + earnedPointIds);
        }
        return found;
    }

    public EarnedPoint byTransaction(Long transactionId) {
        return earnedPointRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> PointException.of(ErrorCode.EARNED_POINT_NOT_FOUND, "transactionId=" + transactionId));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
