package io.github.jualvessan.holdfast.application.reservation;

import io.github.jualvessan.holdfast.application.dto.CreateReservationCommand;
import io.github.jualvessan.holdfast.application.dto.ReservationResult;
import io.github.jualvessan.holdfast.domain.inventory.InsufficientStockException;
import io.github.jualvessan.holdfast.domain.inventory.Sector;
import io.github.jualvessan.holdfast.domain.inventory.SectorNotFoundException;
import io.github.jualvessan.holdfast.domain.reservation.IdempotencyConflictException;
import io.github.jualvessan.holdfast.domain.reservation.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Use case tests run against in-memory fakes: no Spring, no database, no
 * Docker. They verify the orchestration (idempotency, 404 vs 409, expiration
 * deadline). Concurrency correctness is NOT tested here - that belongs to
 * SectorConcurrentReservationIT, against a real Oracle.
 */
class CreateReservationUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);
    private static final String SECTOR_ID = "sector-1";

    private InMemoryReservationRepository reservations;
    private InMemorySectorRepository sectors;
    private CreateReservationUseCase useCase;

    @BeforeEach
    void setUp() {
        reservations = new InMemoryReservationRepository();
        sectors = new InMemorySectorRepository();
        sectors.add(new Sector(SECTOR_ID, "event-1", "Floor", 15_000, 10, 10));

        useCase = new CreateReservationUseCase(
                reservations,
                sectors,
                new SequentialIdGenerator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                HOLD_DURATION);
    }

    @Test
    void createsPendingReservationAndDeductsStock() {
        ReservationResult result = useCase.execute(command(3, "key-1", "hash-a"));

        assertThat(result.reservationId()).isEqualTo("reservation-1");
        assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.quantity()).isEqualTo(3);
        assertThat(sectors.availableOf(SECTOR_ID)).isEqualTo(7);
        assertThat(reservations.all()).hasSize(1);
    }

    @Test
    void holdExpiresAfterConfiguredDuration() {
        ReservationResult result = useCase.execute(command(1, "key-1", "hash-a"));

        assertThat(result.expiresAt()).isEqualTo(NOW.plus(HOLD_DURATION));
    }

    @Test
    void snapshotsSectorPriceIntoTheReservation() {
        ReservationResult result = useCase.execute(command(1, "key-1", "hash-a"));

        assertThat(result.unitPriceCents()).isEqualTo(15_000);
    }

    @Test
    void unknownSectorFailsWithNotFoundAndChangesNothing() {
        CreateReservationCommand command = new CreateReservationCommand("missing", 1, "key-1", "hash-a");

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(SectorNotFoundException.class);

        assertThat(reservations.all()).isEmpty();
    }

    @Test
    void insufficientStockFailsWithoutCreatingAReservation() {
        assertThatThrownBy(() -> useCase.execute(command(11, "key-1", "hash-a")))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(reservations.all()).isEmpty();
        assertThat(sectors.availableOf(SECTOR_ID)).isEqualTo(10);
    }

    @Test
    void retryWithSameKeyAndSameBodyReturnsOriginalWithoutDeductingAgain() {
        ReservationResult first = useCase.execute(command(3, "key-1", "hash-a"));
        ReservationResult retry = useCase.execute(command(3, "key-1", "hash-a"));

        assertThat(retry).isEqualTo(first);
        assertThat(reservations.all()).hasSize(1);
        assertThat(sectors.availableOf(SECTOR_ID))
                .as("stock must be deducted exactly once, not once per retry")
                .isEqualTo(7);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedAsConflict() {
        useCase.execute(command(3, "key-1", "hash-a"));

        assertThatThrownBy(() -> useCase.execute(command(5, "key-1", "hash-b")))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(reservations.all()).hasSize(1);
        assertThat(sectors.availableOf(SECTOR_ID)).isEqualTo(7);
    }

    @Test
    void differentKeysCreateIndependentReservations() {
        ReservationResult first = useCase.execute(command(2, "key-1", "hash-a"));
        ReservationResult second = useCase.execute(command(2, "key-2", "hash-a"));

        assertThat(second.reservationId()).isNotEqualTo(first.reservationId());
        assertThat(sectors.availableOf(SECTOR_ID)).isEqualTo(6);
    }

    private static CreateReservationCommand command(int quantity, String idempotencyKey, String requestHash) {
        return new CreateReservationCommand(SECTOR_ID, quantity, idempotencyKey, requestHash);
    }
}
