package com.musinsa.payments.point.service;

import com.musinsa.payments.point.repository.UserPointLockRepository;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPointLocker {

    private final UserPointLockRepository lockRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public void lock(Long userId) {
        meterRegistry.timer("point.lock.wait").record(() -> acquire(userId));
    }

    private void acquire(Long userId) {
        if (lockRepository.findByUserIdForUpdate(userId).isPresent()) {
            return;
        }
        lockRepository.upsert(userId, LocalDateTime.now(clock));
        lockRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> PointException.of(ErrorCode.INTERNAL_ERROR, "락 획득에 실패했습니다. userId=" + userId));
    }
}
