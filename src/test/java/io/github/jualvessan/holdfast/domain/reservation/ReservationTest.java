package io.github.jualvessan.holdfast.domain.reservation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void newReservationStartsAsPending() {
        Reservation reservation = newPendingReservation();

        assertThat(reservation.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.confirmedAt()).isEmpty();
        assertThat(reservation.cancelledAt()).isEmpty();
    }

    @Test
    void confirmTransitionsPendingToConfirmed() {
        Reservation reservation = newPendingReservation();
        Instant confirmationTime = NOW.plus(2, ChronoUnit.MINUTES);

        Reservation confirmed = reservation.confirm(confirmationTime);

        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).contains(confirmationTime);
        assertThat(confirmed.cancelledAt()).isEmpty();
    }

    @Test
    void cancelTransitionsPendingToCancelled() {
        Reservation reservation = newPendingReservation();

        Reservation cancelled = reservation.cancel(NOW.plusSeconds(30));

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isPresent();
    }

    @Test
    void expireTransitionsPendingToExpired() {
        Reservation reservation = newPendingReservation();

        Reservation expired = reservation.expire(NOW.plus(15, ChronoUnit.MINUTES));

        assertThat(expired.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expired.cancelledAt()).isPresent();
    }

    @Test
    void cannotConfirmAReservationThatIsAlreadyConfirmed() {
        Reservation confirmed = newPendingReservation().confirm(NOW.plusSeconds(10));

        assertThatThrownBy(() -> confirmed.confirm(NOW.plusSeconds(20)))
                .isInstanceOf(InvalidReservationStateTransitionException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void cannotConfirmACancelledReservation() {
        Reservation cancelled = newPendingReservation().cancel(NOW.plusSeconds(10));

        assertThatThrownBy(() -> cancelled.confirm(NOW.plusSeconds(20)))
                .isInstanceOf(InvalidReservationStateTransitionException.class);
    }

    @Test
    void cannotCancelAnAlreadyExpiredReservation() {
        Reservation expired = newPendingReservation().expire(NOW.plus(20, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> expired.cancel(NOW.plus(21, ChronoUnit.MINUTES)))
                .isInstanceOf(InvalidReservationStateTransitionException.class);
    }

    @Test
    void isOverdueIsTrueOnlyWhenPendingAndPastExpiration() {
        Reservation reservation = newPendingReservation(); // expires 10 minutes after NOW

        assertThat(reservation.isOverdue(NOW.plus(5, ChronoUnit.MINUTES))).isFalse();
        assertThat(reservation.isOverdue(NOW.plus(11, ChronoUnit.MINUTES))).isTrue();

        Reservation confirmed = reservation.confirm(NOW.plus(2, ChronoUnit.MINUTES));
        assertThat(confirmed.isOverdue(NOW.plus(11, ChronoUnit.MINUTES)))
                .as("a confirmed reservation is never overdue, regardless of the original deadline")
                .isFalse();
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> Reservation.createPending(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0, 10_000,
                "idem-key", "hash", NOW.plus(10, ChronoUnit.MINUTES), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    private static Reservation newPendingReservation() {
        return Reservation.createPending(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                2,
                10_000,
                "idem-" + UUID.randomUUID(),
                "hash",
                NOW.plus(10, ChronoUnit.MINUTES),
                NOW);
    }
}
