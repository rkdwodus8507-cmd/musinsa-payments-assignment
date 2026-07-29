package com.musinsa.payments.point.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pointOpenApi() {
        return new OpenAPI().info(new Info()
                .title("무료 포인트 시스템 API")
                .description("적립 / 적립취소 / 사용 / 사용취소 및 정책 관리 API")
                .version("1.0.0"));
    }
}
