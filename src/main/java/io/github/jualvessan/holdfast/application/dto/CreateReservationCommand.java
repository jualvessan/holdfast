package io.github.jualvessan.holdfast.application.dto;

/**
 * Input for {@link io.github.jualvessan.holdfast.application.reservation.CreateReservationUseCase}.
 * {@code requestHash} is computed by the web layer from the raw request body,
 * so the use case never needs to know the wire format (JSON, etc).
 */
public record CreateReservationCommand(
        String sectorId,
        int quantity,
        String idempotencyKey,
        String requestHash
) {
}
