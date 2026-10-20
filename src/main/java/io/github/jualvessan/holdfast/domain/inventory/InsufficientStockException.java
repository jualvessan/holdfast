package io.github.jualvessan.holdfast.domain.inventory;

import io.github.jualvessan.holdfast.domain.shared.DomainException;

/**
 * Thrown when a reservation attempt cannot be satisfied because the sector
 * does not have enough available units. The web layer translates this into
 * {@code 409 Conflict}.
 */
public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String sectorId, int requestedQuantity) {
        super("Sector [%s] does not have %d available unit(s)".formatted(sectorId, requestedQuantity));
    }
}
