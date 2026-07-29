package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.service.dto.ExpirationResult;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExpiredPointMarker {

    private final EarnedPointRepository earnedPointRepository;

    @Transactional
    public ExpirationResult markChunk(LocalDateTime baseTime, int chunkSize) {
        List<EarnedPoint> targets = earnedPointRepository.findExpirationTargets(
                EarnedPointStatus.AVAILABLE, baseTime, PageRequest.of(0, chunkSize));

        long expiredAmount = 0;
        for (EarnedPoint earnedPoint : targets) {
            expiredAmount += earnedPoint.getRemainingAmount();
            earnedPoint.expire();
        }
        return new ExpirationResult(targets.size(), expiredAmount);
    }
}
