package com.musinsa.payments.point.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.ZoneId;

@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(ZoneId.systemDefault());
    }
}
