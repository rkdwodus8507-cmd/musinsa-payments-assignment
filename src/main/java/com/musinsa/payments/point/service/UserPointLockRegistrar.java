package com.musinsa.payments.point.service;

import java.time.LocalDateTime;

public interface UserPointLockRegistrar {

    void registerIfAbsent(Long userId, LocalDateTime now);
}
