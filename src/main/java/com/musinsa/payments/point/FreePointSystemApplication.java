package com.musinsa.payments.point;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class FreePointSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreePointSystemApplication.class, args);
    }
}
