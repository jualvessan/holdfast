package io.github.jualvessan.holdfast.infrastructure.persistence;

import io.github.jualvessan.holdfast.domain.reservation.Reservation;
import io.github.jualvessan.holdfast.domain.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationRepositoryAdapterIT extends OracleTestSupport {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final ReservationRepositoryAdapter reservations = new ReservationRepositoryAdapter(newJdbcTemplate());

    @Test
    void savedReservationIsReadBackWithEveryField() {
        Reservation reservation = pendingReservation(newSector(10, 5_000), "key-" + UUID.randomUUID());

        reservations.save(reservation);

        Reservation found = reservations.findById(reservation.id()).orElseThrow();
        assertThat(found).usingRecursiveComparison().isEqualTo(reservation);
    }

    @Test
    void findsAReservationByItsIdempotencyKey() {
        String key = "key-" + UUID.randomUUID();
        Reservation reservation = pendingReservation(newSector(10, 5_000), key);
        reservations.save(reservation);

        assertThat(reservations.findByIdempotencyKey(key)).get().extracting(Reservation::id)
                .isEqualTo(reservation.id());
    }

    @Test
    void unknownIdempotencyKeyIsEmpty() {
        assertThat(reservations.findByIdempotencyKey("key-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void unknownOrMalformedIdIsEmpty() {
        assertThat(reservations.findById(UUID.randomUUID().toString())).isEmpty();
        assertThat(reservations.findById("not-a-uuid")).isEmpty();
    }

    @Test
    void updatePersistsAStateTransition() {
        Reservation pending = pendingReservation(newSector(10, 5_000), "key-" + UUID.randomUUID());
        reservations.save(pending);
        Instant confirmationTime = NOW.plus(3, ChronoUnit.MINUTES);

        reservations.update(pending.confirm(confirmationTime));

        Reservation found = reservations.findById(pending.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(found.confirmedAt()).contains(confirmationTime);
        assertThat(found.updatedAt()).isEqualTo(confirmationTime);
        assertThat(found.cancelledAt()).isEmpty();
    }

    @Test
    void theDatabaseRejectsTwoReservationsWithTheSameIdempotencyKey() {
        String sectorId = newSector(10, 5_000);
        String key = "key-" + UUID.randomUUID();
        reservations.save(pendingReservation(sectorId, key));

        assertThatThrownBy(() -> reservations.save(pendingReservation(sectorId, key)))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(countReservationsWithKey(key)).isEqualTo(1);
    }

    @Test
    void updatingAReservationThatDoesNotExistIsAnError() {
        Reservation neverSaved = pendingReservation(newSector(10, 5_000), "key-" + UUID.randomUUID());

        assertThatThrownBy(() -> reservations.update(neverSaved.cancel(NOW.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Reservation pendingReservation(String sectorId, String idempotencyKey) {
        return Reservation.createPending(
                UUID.randomUUID().toString(),
                sectorId,
                2,
                5_000,
                idempotencyKey,
                "hash-a",
                NOW.plus(10, ChronoUnit.MINUTES),
                NOW);
    }
}
