package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointLot;
import com.musinsa.payments.point.domain.PointLotStatus;
import com.musinsa.payments.point.repository.PointLotRepository;
import com.musinsa.payments.point.service.dto.PointResults;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointExpirationProcessor {

    private final PointLotRepository lotRepository;

    @Transactional
    public PointResults.Expiration expireChunk(LocalDateTime baseTime, int chunkSize) {
        List<PointLot> targets = lotRepository.findExpirationTargets(
                PointLotStatus.AVAILABLE, baseTime, PageRequest.of(0, chunkSize));
        long amount = 0;
        for (PointLot lot : targets) {
            amount += lot.getRemainingAmount();
            lot.expire();
        }
        return new PointResults.Expiration(targets.size(), amount);
    }
}
