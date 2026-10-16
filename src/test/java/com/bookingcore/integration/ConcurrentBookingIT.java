package com.bookingcore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.jayway.jsonpath.JsonPath;

/**
 * Proves that double bookings are impossible under real concurrency.
 *
 * <p>Competing requests are sent over real HTTP to the embedded server, each on its own thread, so each is
 * handled by its own servlet thread, its own database connection and its own transaction - exactly as in
 * production. A {@link CyclicBarrier} releases all threads at the same instant to maximise contention.
 * Afterwards PostgreSQL itself is queried to verify the invariant, independently of the API responses.
 */
@DisplayName("Concurrent booking")
class ConcurrentBookingIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    private HttpClient http;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build();
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        http.close();
    }

    @Test
    @DisplayName("two simultaneous requests for the same interval: exactly one succeeds, the other gets 409")
    void twoSimultaneousRequestsForSameInterval() throws Exception {
        UUID roomId = createResource();
        OffsetDateTime start = BASE.withHour(10);
        OffsetDateTime end = BASE.withHour(11);

        List<HttpResponse<String>> responses = raceAll(List.of(
                () -> bookOverHttp(roomId, start, end),
                () -> bookOverHttp(roomId, start, end)));

        Map<Integer, Long> statusCounts = countByStatus(responses);
        assertThat(statusCounts).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, 1L));

        HttpResponse<String> rejected = responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow();
        assertThat(rejected.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat((String) JsonPath.read(rejected.body(), "$.code")).isEqualTo("RESERVATION_CONFLICT");

        assertThat(confirmedOverlapping(roomId, start, end)).isEqualTo(1);
    }

    @RepeatedTest(value = 20, name = "{displayName} - run {currentRepetition}/{totalRepetitions}")
    @DisplayName("8 simultaneous requests for the same interval: exactly one succeeds")
    void manySimultaneousRequestsForSameInterval() throws Exception {
        UUID roomId = createResource();
        OffsetDateTime start = BASE.withHour(14);
        OffsetDateTime end = BASE.withHour(15);

        List<Callable<HttpResponse<String>>> attempts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            attempts.add(() -> bookOverHttp(roomId, start, end));
        }
        List<HttpResponse<String>> responses = raceAll(attempts);

        assertThat(countByStatus(responses)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, 7L));
        assertThat(confirmedOverlapping(roomId, start, end)).isEqualTo(1);
    }

    @RepeatedTest(value = 5, name = "{displayName} - run {currentRepetition}/{totalRepetitions}")
    @DisplayName("random partially-overlapping intervals under contention never produce an overlap")
    void randomOverlappingIntervalsNeverProduceOverlap() throws Exception {
        UUID roomId = createResource();
        OffsetDateTime windowStart = BASE.withHour(8);

        List<Callable<HttpResponse<String>>> attempts = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            // 15-minute granularity inside a 4-hour window, 30-90 minutes long: many partial overlaps.
            int startOffset = ThreadLocalRandom.current().nextInt(0, 16) * 15;
            int duration = ThreadLocalRandom.current().nextInt(2, 7) * 15;
            OffsetDateTime start = windowStart.plusMinutes(startOffset);
            OffsetDateTime end = start.plusMinutes(duration);
            attempts.add(() -> bookOverHttp(roomId, start, end));
        }
        List<HttpResponse<String>> responses = raceAll(attempts);

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isIn(201, 409));
        long created = responses.stream().filter(r -> r.statusCode() == 201).count();
        assertThat(created).isPositive();

        Integer stored = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservations WHERE resource_id = ? AND status = 'CONFIRMED'",
                Integer.class, roomId);
        assertThat(stored).isEqualTo((int) created);

        Integer overlappingPairs = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM reservations a
                        JOIN reservations b ON a.resource_id = b.resource_id AND a.id < b.id
                        WHERE a.resource_id = ? AND a.status = 'CONFIRMED' AND b.status = 'CONFIRMED'
                          AND tstzrange(a.start_time, a.end_time, '[)') && tstzrange(b.start_time, b.end_time, '[)')
                        """,
                Integer.class, roomId);
        assertThat(overlappingPairs).isZero();
    }

    @Test
    @DisplayName("simultaneous requests for different rooms at the same time all succeed")
    void simultaneousRequestsForDifferentRoomsAllSucceed() throws Exception {
        UUID roomA = createResource();
        UUID roomB = createResource();
        OffsetDateTime start = BASE.withHour(9);
        OffsetDateTime end = BASE.withHour(10);

        List<HttpResponse<String>> responses = raceAll(List.of(
                () -> bookOverHttp(roomA, start, end),
                () -> bookOverHttp(roomB, start, end)));

        assertThat(countByStatus(responses)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 2L));
        assertThat(confirmedOverlapping(roomA, start, end)).isEqualTo(1);
        assertThat(confirmedOverlapping(roomB, start, end)).isEqualTo(1);
    }

    @Test
    @DisplayName("a concurrent update and create competing for the same slot: exactly one wins")
    void concurrentUpdateAndCreateForSameSlot() throws Exception {
        UUID roomId = createResource();
        UUID existing = createReservation(roomId, BASE.withHour(8), BASE.withHour(9));
        OffsetDateTime contestedStart = BASE.withHour(12);
        OffsetDateTime contestedEnd = BASE.withHour(13);

        List<HttpResponse<String>> responses = raceAll(List.of(
                () -> moveOverHttp(existing, contestedStart, contestedEnd),
                () -> bookOverHttp(roomId, contestedStart, contestedEnd)));

        assertThat(responses.stream().filter(r -> r.statusCode() / 100 == 2).count()).isEqualTo(1);
        assertThat(responses.stream().filter(r -> r.statusCode() == 409).count()).isEqualTo(1);
        assertThat(confirmedOverlapping(roomId, contestedStart, contestedEnd)).isEqualTo(1);
    }

    /** Starts all tasks on separate threads, releases them simultaneously and waits for all results. */
    private <T> List<T> raceAll(List<Callable<T>> tasks) throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(tasks.size());
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                startLine.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                return task.call();
            }));
        }
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        }
        return results;
    }

    private HttpResponse<String> bookOverHttp(UUID resourceId, OffsetDateTime start, OffsetDateTime end)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/reservations"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reservationJson(resourceId, start, end)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> moveOverHttp(UUID reservationId, OffsetDateTime start, OffsetDateTime end)
            throws Exception {
        String body = """
                {"startTime": "%s", "endTime": "%s"}
                """.formatted(start, end);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/reservations/" + reservationId))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Map<Integer, Long> countByStatus(List<HttpResponse<String>> responses) {
        return responses.stream().collect(Collectors.groupingBy(HttpResponse::statusCode, Collectors.counting()));
    }
}
