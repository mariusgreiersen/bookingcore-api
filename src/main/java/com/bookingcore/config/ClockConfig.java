package com.bookingcore.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the clock as a bean so time-dependent rules (e.g. "reservations cannot start in the past") can be
 * tested deterministically with a fixed clock.
 *
 * <p>The clock ticks in whole microseconds, the precision of PostgreSQL {@code timestamptz}. Otherwise an
 * entity's {@code updatedAt} would carry nanoseconds in the response to the write, but a rounded value when read
 * back later, so the API would report two different values for the same stored timestamp.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
