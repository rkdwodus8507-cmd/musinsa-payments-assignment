package com.musinsa.payments.point.config;

import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "point")
public class PointTimeProperties {

    private String timeZone;

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }
}
