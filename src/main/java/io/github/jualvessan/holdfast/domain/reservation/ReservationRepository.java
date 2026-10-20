package io.github.jualvessan.holdfast.domain.reservation;

import java.util.Optional;

/**
 * Port for reservation persistence. The implementation lives in the
 * infrastructure layer.
 */
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Reservation update(Reservation reservation);

    Optional<Reservation> findById(String id);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
}
