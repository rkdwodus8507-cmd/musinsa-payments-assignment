package com.musinsa.payments.point.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class MutableClock extends Clock {

    public static final LocalDateTime INITIAL_TIME = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

    private final ZoneId zone;
    private Instant instant;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
        this.instant = INITIAL_TIME.atZone(zone).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId targetZone) {
        MutableClock clock = new MutableClock(targetZone);
        clock.instant = this.instant;
        return clock;
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(instant, zone);
    }

    public void reset() {
        this.instant = INITIAL_TIME.atZone(zone).toInstant();
    }

    public void plusDays(long days) {
        this.instant = this.instant.plus(Duration.ofDays(days));
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.instant = dateTime.atZone(zone).toInstant();
    }
}
