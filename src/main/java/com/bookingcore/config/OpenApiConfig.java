package com.bookingcore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI bookingCoreOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BookingCore API")
                .version("v1")
                .description("""
                        Reservation management for limited-availability resources such as meeting rooms.

                        **Double-booking guarantee:** overlapping confirmed reservations for the same resource are \
                        rejected by a PostgreSQL exclusion constraint, so the guarantee holds under concurrent \
                        requests and across multiple application instances. Conflicts return `409` with \
                        `code: RESERVATION_CONFLICT`.

                        **Time semantics:** intervals are half-open `[startTime, endTime)`, so back-to-back bookings \
                        are allowed. Timestamps must be ISO 8601 with an explicit offset; responses are in UTC. \
                        In query strings, encode `+` as `%2B` or use `Z`.

                        **Availability** responses are informational only and do not reserve anything.

                        **Errors** use RFC 9457 Problem Details with a stable `code` and a `requestId`.

                        **Note:** this MVP has no authentication; do not expose it publicly.""")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
