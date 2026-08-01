package com.musinsa.payments.point.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "point.expiration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PointExpirationProperties {

    private boolean enabled;

    private String cron;

    private int chunkSize;
}
