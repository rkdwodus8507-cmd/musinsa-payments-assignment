package com.musinsa.payments.point.config;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ClockConfig {

    private final PointTimeProperties timeProperties;

    @Bean
    public Clock clock() {
        return Clock.system(timeProperties.zoneId());
    }
}
