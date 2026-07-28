package com.musinsa.payments.point.service;

import com.musinsa.payments.point.config.PointExpirationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "point.expiration", name = "enabled", havingValue = "true")
public class PointExpirationScheduler {

    private final PointExpirationService expirationService;
    private final PointExpirationProperties expirationProperties;

    @Scheduled(cron = "${point.expiration.cron}")
    public void expirePoints() {
        expirationService.expireAll(expirationProperties.chunkSize());
    }
}
