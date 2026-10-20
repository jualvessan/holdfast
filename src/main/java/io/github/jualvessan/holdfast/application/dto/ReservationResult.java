package io.github.jualvessan.holdfast.application.dto;

import io.github.jualvessan.holdfast.domain.reservation.ReservationStatus;

import java.time.Instant;

/**
 * Output of reservation use cases. Deliberately a flat, framework-agnostic
 * shape - the web layer maps this into whatever HTTP response DTO it needs,
 * but the application layer itself knows nothing about HTTP or JSON.
 */
public record ReservationResult(
        String reservationId,
        String sectorId,
        int quantity,
        long unitPriceCents,
        ReservationStatus status,
        Instant expiresAt
) {
}
