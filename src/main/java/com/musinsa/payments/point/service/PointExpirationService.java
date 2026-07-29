package com.musinsa.payments.point.service;

import com.musinsa.payments.point.service.dto.ExpirationResult;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointExpirationService {

    private final ExpiredPointMarker expiredPointMarker;
    private final Clock clock;

    public ExpirationResult expireAll(int chunkSize) {
        LocalDateTime baseTime = LocalDateTime.now(clock);
        int totalCount = 0;
        long totalAmount = 0;

        ExpirationResult chunk;
        do {
            chunk = expiredPointMarker.markChunk(baseTime, chunkSize);
            totalCount += chunk.expiredCount();
            totalAmount += chunk.expiredAmount();
        } while (chunk.expiredCount() == chunkSize);

        if (totalCount > 0) {
            log.info("expired {} earned points, {} points at {}", totalCount, totalAmount, baseTime);
        }
        return new ExpirationResult(totalCount, totalAmount);
    }
}
