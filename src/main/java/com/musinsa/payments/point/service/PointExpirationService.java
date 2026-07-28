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

    private final PointExpirationProcessor expirationProcessor;
    private final Clock clock;

    public ExpirationResult expireAll(int chunkSize) {
        LocalDateTime baseTime = LocalDateTime.now(clock);
        int totalCount = 0;
        long totalAmount = 0;
        while (true) {
            ExpirationResult chunk = expirationProcessor.expireChunk(baseTime, chunkSize);
            totalCount += chunk.expiredLotCount();
            totalAmount += chunk.expiredAmount();
            if (chunk.expiredLotCount() < chunkSize) {
                break;
            }
        }
        if (totalCount > 0) {
            log.info("expired {} lots, {} points at {}", totalCount, totalAmount, baseTime);
        }
        return new ExpirationResult(totalCount, totalAmount);
    }
}
