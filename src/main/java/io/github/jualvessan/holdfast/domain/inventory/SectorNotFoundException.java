package io.github.jualvessan.holdfast.domain.inventory;

import io.github.jualvessan.holdfast.domain.shared.DomainException;

/**
 * Thrown when a reservation is requested for a sector that does not exist.
 * The web layer translates this into {@code 404 Not Found} - distinct from
 * {@link InsufficientStockException}, which means the sector exists but has
 * no stock left ({@code 409 Conflict}).
 */
public class SectorNotFoundException extends DomainException {

    public SectorNotFoundException(String sectorId) {
        super("Sector [%s] was not found".formatted(sectorId));
    }
}
