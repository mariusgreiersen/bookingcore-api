package com.bookingcore;

import org.springframework.boot.SpringApplication;

import com.bookingcore.integration.TestcontainersConfiguration;

/**
 * Runs the application locally against a throwaway PostgreSQL container, without installing PostgreSQL:
 * {@code ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=demo}. Requires a running Docker daemon.
 */
public class TestBookingCoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(BookingCoreApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
