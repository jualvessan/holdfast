package io.github.jualvessan.holdfast.infrastructure.config;

import io.github.jualvessan.holdfast.application.dto.CreateReservationCommand;
import io.github.jualvessan.holdfast.application.dto.ReservationResult;
import io.github.jualvessan.holdfast.application.reservation.CreateReservationUseCase;
import io.github.jualvessan.holdfast.domain.inventory.InsufficientStockException;
import io.github.jualvessan.holdfast.infrastructure.persistence.OracleTestSupport;
import io.github.jualvessan.holdfast.infrastructure.persistence.ReservationRepositoryAdapter;
import io.github.jualvessan.holdfast.infrastructure.persistence.SectorRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full create-reservation flow (use case + transaction + real adapters +
 * real Oracle) under concurrency, without HTTP or a Spring context. The wiring
 * is done by hand, which keeps the test fast and shows exactly which pieces
 * cooperate.
 *
 * <p>These two tests are the end-to-end counterpart of
 * SectorConcurrentReservationIT: that one proves the single UPDATE statement,
 * these prove the whole flow around it.
 */
class CreateReservationFlowIT extends OracleTestSupport {

    private final TransactionalCreateReservation service = buildService();

    @Test
    void manyConcurrentRetriesWithTheSameKeyCreateOneReservationAndDeductStockOnce() throws Exception {
        String sectorId = newSector(100, 10_000);
        CreateReservationCommand command = new CreateReservationCommand(
                sectorId, 3, "key-" + UUID.randomUUID(), "hash-a");
        int callers = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.execute(command);
                }));
            }
            start.countDown();
        }

        Set<String> reservationIds = new HashSet<>();
        for (Future<ReservationResult> future : futures) {
            reservationIds.add(future.get().reservationId());
        }

        assertThat(reservationIds)
                .as("every caller must receive the same reservation, whoever won the race")
                .hasSize(1);
        assertThat(countReservationsWithKey(command.idempotencyKey()))
                .as("exactly one row for the key")
                .isEqualTo(1);
        assertThat(availableOf(sectorId))
                .as("stock deducted once (100 - 3), not once per caller")
                .isEqualTo(97);
    }

    @Test
    void concurrentRetriesOfTheRequestThatTakesTheLastUnitsStillGetTheOriginal() throws Exception {
        String sectorId = newSector(3, 10_000);
        CreateReservationCommand command = new CreateReservationCommand(
                sectorId, 3, "key-" + UUID.randomUUID(), "hash-a");
        int callers = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.execute(command);
                }));
            }
            start.countDown();
        }

        Set<String> reservationIds = new HashSet<>();
        for (Future<ReservationResult> future : futures) {
            reservationIds.add(future.get().reservationId()); // no InsufficientStockException expected
        }

        assertThat(reservationIds).hasSize(1);
        assertThat(availableOf(sectorId)).isZero();
        assertThat(countReservationsWithKey(command.idempotencyKey())).isEqualTo(1);
    }

    @Test
    void distinctRequestsNeverOversellTheSector() throws Exception {
        int capacity = 100;
        int requests = 200;
        String sectorId = newSector(capacity, 10_000);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < requests; i++) {
                String key = "key-" + i + "-" + sectorId;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.execute(new CreateReservationCommand(sectorId, 1, key, "hash"));
                        created.incrementAndGet();
                    } catch (InsufficientStockException noStockLeft) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
        }

        for (Future<Object> future : futures) {
            future.get(); // surfaces any unexpected exception from a worker
        }

        assertThat(created.get()).isEqualTo(capacity);
        assertThat(rejected.get()).isEqualTo(requests - capacity);
        assertThat(availableOf(sectorId)).isZero();
        assertThat(countReservationsOfSector(sectorId)).isEqualTo(capacity);
    }

    private static TransactionalCreateReservation buildService() {
        JdbcTemplate jdbc = newJdbcTemplate();
        CreateReservationUseCase useCase = new CreateReservationUseCase(
                new ReservationRepositoryAdapter(jdbc),
                new SectorRepositoryAdapter(jdbc),
                () -> UUID.randomUUID().toString(),
                Clock.tickMillis(ZoneOffset.UTC),
                Duration.ofMinutes(10));
        return new TransactionalCreateReservation(useCase, new DataSourceTransactionManager(DATA_SOURCE));
    }
}
