package com.musinsa.payments.point.service;

import com.musinsa.payments.point.repository.PointWalletRepository;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PointWalletLocker {

    private final PointWalletRepository walletRepository;
    private final Clock clock;

    public void lock(Long userId) {
        if (walletRepository.findByUserIdForUpdate(userId).isPresent()) {
            return;
        }
        walletRepository.upsert(userId, LocalDateTime.now(clock));
        walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> PointException.of(ErrorCode.INTERNAL_ERROR, "지갑 락 획득에 실패했습니다. userId=" + userId));
    }
}
