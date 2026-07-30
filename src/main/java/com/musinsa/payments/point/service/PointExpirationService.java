package com.musinsa.payments.point.service;

import com.musinsa.payments.point.config.PointExpirationProperties;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.service.dto.ExpirationResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointExpirationService {

    private final EarnedPointRepository earnedPointRepository;
    private final ExpiredPointMarker expiredPointMarker;
    private final PointExpirationProperties expirationProperties;
    private final PointAuditRecorder auditRecorder;
    private final Clock clock;

    public ExpirationResult expireAll() {
        LocalDateTime baseTime = LocalDateTime.now(clock);
        ExpirationResult expired = expireEveryOwner(baseTime);

        auditRecorder.recordExpiration(expired, baseTime);
        return expired;
    }

    private ExpirationResult expireEveryOwner(LocalDateTime baseTime) {
        int totalCount = 0;
        long totalAmount = 0;

        List<Long> owners = ownersOfExpirablePoints(baseTime);
        while (!owners.isEmpty()) {
            for (Long userId : owners) {
                ExpirationResult expired = expiredPointMarker.markFor(userId, baseTime);
                totalCount += expired.getExpiredCount();
                totalAmount += expired.getExpiredAmount();
            }
            owners = ownersOfExpirablePoints(baseTime);
        }
        return new ExpirationResult(totalCount, totalAmount);
    }

    private List<Long> ownersOfExpirablePoints(LocalDateTime baseTime) {
        return earnedPointRepository.findOwnersOfExpirablePoints(
                EarnedPointStatus.AVAILABLE, baseTime, PageRequest.of(0, expirationProperties.getChunkSize()));
    }
}
