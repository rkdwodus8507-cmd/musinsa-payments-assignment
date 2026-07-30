package com.musinsa.payments.point.support.batch;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BatchLockStore {

    public static final LocalDateTime RELEASED_AT = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final BatchLockRepository lockRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String name, String holder, Duration ttl) {
        LocalDateTime now = LocalDateTime.now(clock);
        return lockRepository.acquire(name, holder, now, now.plus(ttl)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String name, String holder) {
        lockRepository.release(name, holder, RELEASED_AT);
    }
}
