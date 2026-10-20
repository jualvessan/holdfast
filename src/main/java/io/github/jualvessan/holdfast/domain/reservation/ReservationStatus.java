package io.github.jualvessan.holdfast.domain.reservation;

/**
 * Mirrors the {@code CHECK} constraint on the {@code reservation.status}
 * column (see migration V3). Keeping the two in sync is a manual discipline
 * for now; ArchUnit / integration tests introduced in later phases should
 * guard against drift.
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    EXPIRED,
    CANCELLED
}
