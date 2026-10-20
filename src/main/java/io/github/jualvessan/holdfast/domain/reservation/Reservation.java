package io.github.jualvessan.holdfast.domain.reservation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static io.github.jualvessan.holdfast.domain.reservation.ReservationStatus.CANCELLED;
import static io.github.jualvessan.holdfast.domain.reservation.ReservationStatus.CONFIRMED;
import static io.github.jualvessan.holdfast.domain.reservation.ReservationStatus.EXPIRED;
import static io.github.jualvessan.holdfast.domain.reservation.ReservationStatus.PENDING;

/**
 * A temporary hold (and, once confirmed, a sale) of {@code quantity} units of
 * a single sector. Allowed transitions:
 *
 * <pre>
 *            confirm()
 *   PENDING ───────────► CONFIRMED
 *      │
 *      ├── cancel() ───► CANCELLED
 *      └── expire() ───► EXPIRED
 * </pre>
 *
 * CONFIRMED, CANCELLED and EXPIRED are terminal: no further transition is
 * allowed from them. Instances are immutable; every transition returns a new
 * instance, so the caller (an application use case) is responsible for
 * persisting the result via {@link ReservationRepository}.
 *
 * <p>Intentionally out of scope for this phase (see ADR-0001 "no
 * overengineering" principle): multi-item reservations (a single reservation
 * spanning several sectors). A reservation always refers to exactly one
 * sector; see ADR-0001's context section for the rationale.
 */
public final class Reservation {

    private final String id;
    private final String sectorId;
    private final int quantity;
    private final long unitPriceCents;
    private final ReservationStatus status;
    private final String idempotencyKey;
    private final String requestHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant confirmedAt;
    private final Instant cancelledAt;

    private Reservation(String id, String sectorId, int quantity, long unitPriceCents,
                         ReservationStatus status, String idempotencyKey, String requestHash,
                         Instant expiresAt, Instant createdAt, Instant updatedAt,
                         Instant confirmedAt, Instant cancelledAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sectorId = Objects.requireNonNull(sectorId, "sectorId must not be null");
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got " + quantity);
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("unitPriceCents must not be negative, got " + unitPriceCents);
        }
    }

    /** Creates a brand-new reservation in PENDING status. */
    public static Reservation createPending(String id, String sectorId, int quantity, long unitPriceCents,
                                             String idempotencyKey, String requestHash,
                                             Instant expiresAt, Instant now) {
        return new Reservation(id, sectorId, quantity, unitPriceCents, PENDING,
                idempotencyKey, requestHash, expiresAt, now, now, null, null);
    }

    /** Rehydrates a reservation from persisted state. Used by the repository adapter. */
    public static Reservation rehydrate(String id, String sectorId, int quantity, long unitPriceCents,
                                        ReservationStatus status, String idempotencyKey, String requestHash,
                                        Instant expiresAt, Instant createdAt, Instant updatedAt,
                                        Instant confirmedAt, Instant cancelledAt) {
        return new Reservation(id, sectorId, quantity, unitPriceCents, status, idempotencyKey,
                requestHash, expiresAt, createdAt, updatedAt, confirmedAt, cancelledAt);
    }

    public Reservation confirm(Instant now) {
        requireStatus(PENDING, CONFIRMED);
        return new Reservation(id, sectorId, quantity, unitPriceCents, CONFIRMED,
                idempotencyKey, requestHash, expiresAt, createdAt, now, now, cancelledAt);
    }

    public Reservation cancel(Instant now) {
        requireStatus(PENDING, CANCELLED);
        return new Reservation(id, sectorId, quantity, unitPriceCents, CANCELLED,
                idempotencyKey, requestHash, expiresAt, createdAt, now, confirmedAt, now);
    }

    public Reservation expire(Instant now) {
        requireStatus(PENDING, EXPIRED);
        return new Reservation(id, sectorId, quantity, unitPriceCents, EXPIRED,
                idempotencyKey, requestHash, expiresAt, createdAt, now, confirmedAt, now);
    }

    /** True when this reservation is still PENDING but past its hold deadline. */
    public boolean isOverdue(Instant now) {
        return status == PENDING && now.isAfter(expiresAt);
    }

    private void requireStatus(ReservationStatus required, ReservationStatus target) {
        if (status != required) {
            throw new InvalidReservationStateTransitionException(status, target);
        }
    }

    public String id() {
        return id;
    }

    public String sectorId() {
        return sectorId;
    }

    public int quantity() {
        return quantity;
    }

    public long unitPriceCents() {
        return unitPriceCents;
    }

    public ReservationStatus status() {
        return status;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<Instant> confirmedAt() {
        return Optional.ofNullable(confirmedAt);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
