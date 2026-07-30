package com.musinsa.payments.point.support.batch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchLockManager {

    private final BatchLockStore lockStore;
    private final String holder;

    public BatchLockManager(BatchLockStore lockStore) {
        this.lockStore = lockStore;
        this.holder = resolveHolder();
    }

    public <T> T runExclusively(String name, Duration ttl, Supplier<T> task, Supplier<T> whenHeldByOthers) {
        if (!lockStore.tryAcquire(name, holder, ttl)) {
            log.info("skipped batch {} — another instance holds the lock", name);
            return whenHeldByOthers.get();
        }
        try {
            return task.get();
        } finally {
            lockStore.release(name, holder);
        }
    }

    private static String resolveHolder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + suffix;
        } catch (UnknownHostException e) {
            return "unknown-" + suffix;
        }
    }
}
