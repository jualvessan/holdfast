package io.github.jualvessan.holdfast.domain.inventory;

import java.util.Optional;

/**
 * Port (in the ports-and-adapters sense) for sector persistence. The
 * implementation lives in the infrastructure layer.
 */
public interface SectorRepository {

    Optional<Sector> findById(String sectorId);

    /**
     * Attempts to atomically deduct {@code quantity} units from the sector's
     * available stock. This is the concurrency-safe operation described in
     * ADR-0001: a single conditional {@code UPDATE}, not a
     * read-then-write sequence.
     *
     * @return {@code true} if the deduction succeeded (enough stock was
     *         available), {@code false} if there was not enough stock.
     */
    boolean tryReserve(String sectorId, int quantity);

    /**
     * Returns previously deducted units back to the sector's available stock.
     * Used when a reservation is cancelled or expires before confirmation.
     */
    void release(String sectorId, int quantity);
}
