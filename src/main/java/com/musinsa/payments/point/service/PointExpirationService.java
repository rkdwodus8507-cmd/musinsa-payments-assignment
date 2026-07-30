package com.musinsa.payments.point.service;

import com.musinsa.payments.point.config.PointExpirationProperties;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.service.dto.ExpirationResult;
import com.musinsa.payments.point.support.batch.BatchLockManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointExpirationService {

    public static final String EXPIRATION_LOCK = "point-expiration";

    private final EarnedPointRepository earnedPointRepository;
    private final ExpiredPointMarker expiredPointMarker;
    private final PointExpirationProperties expirationProperties;
    private final PointAuditRecorder auditRecorder;
    private final BatchLockManager batchLockManager;
    private final Clock clock;

    public ExpirationResult expireAll() {
        return batchLockManager.runExclusively(
                EXPIRATION_LOCK,
                expirationProperties.getLockTtl(),
                this::expireEveryOwnerAndRecord,
                () -> new ExpirationResult(0, 0));
    }

    private ExpirationResult expireEveryOwnerAndRecord() {
        LocalDateTime baseTime = LocalDateTime.now(clock);
        auditRecorder.recordExpirationBacklog(countExpirablePoints(baseTime));

        long startedAt = System.nanoTime();
        ExpirationResult expired = expireEveryOwner(baseTime);

        auditRecorder.recordExpiration(expired, baseTime, System.nanoTime() - startedAt);
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

    private long countExpirablePoints(LocalDateTime baseTime) {
        return earnedPointRepository.countExpirablePoints(EarnedPointStatus.AVAILABLE, baseTime);
    }
}
