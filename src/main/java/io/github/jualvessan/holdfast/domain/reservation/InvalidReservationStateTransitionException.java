package io.github.jualvessan.holdfast.domain.reservation;

import io.github.jualvessan.holdfast.domain.shared.DomainException;

/**
 * Thrown when a reservation state transition is attempted from a status that
 * does not allow it (e.g. confirming a reservation that is already
 * cancelled). The web layer translates this into {@code 409 Conflict}.
 */
public class InvalidReservationStateTransitionException extends DomainException {

    public InvalidReservationStateTransitionException(ReservationStatus from, ReservationStatus to) {
        super("Cannot transition reservation from %s to %s".formatted(from, to));
    }
}
