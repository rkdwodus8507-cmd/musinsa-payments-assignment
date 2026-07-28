package com.musinsa.payments.point.config;

import com.musinsa.payments.point.service.PointPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PointPolicyInitializer {

    private final PointPolicyService policyService;

    @Bean
    public ApplicationRunner pointPolicySeeder() {
        return args -> policyService.initializeIfAbsent();
    }
}
