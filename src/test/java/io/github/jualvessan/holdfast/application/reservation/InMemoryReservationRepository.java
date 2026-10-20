package io.github.jualvessan.holdfast.application.reservation;

import io.github.jualvessan.holdfast.domain.reservation.Reservation;
import io.github.jualvessan.holdfast.domain.reservation.ReservationRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake (not a mock): a simple, working in-memory implementation of the port.
 * Tests assert on resulting state, not on which methods were called, so they
 * keep passing when the use case's internals are refactored.
 */
class InMemoryReservationRepository implements ReservationRepository {

    private final Map<String, Reservation> byId = new HashMap<>();

    @Override
    public Reservation save(Reservation reservation) {
        byId.put(reservation.id(), reservation);
        return reservation;
    }

    @Override
    public Reservation update(Reservation reservation) {
        byId.put(reservation.id(), reservation);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
        return byId.values().stream()
                .filter(reservation -> reservation.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    List<Reservation> all() {
        return new ArrayList<>(byId.values());
    }
}
