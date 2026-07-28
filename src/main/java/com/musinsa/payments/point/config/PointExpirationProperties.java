package com.musinsa.payments.point.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "point.expiration")
public record PointExpirationProperties(
        boolean enabled,
        String cron,
        int chunkSize
) {
}
