package io.github.jualvessan.holdfast.application.reservation;

import io.github.jualvessan.holdfast.domain.inventory.Sector;
import io.github.jualvessan.holdfast.domain.inventory.SectorRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake of the sector port. It mimics the contract of the real
 * adapter: {@code tryReserve} succeeds only when enough stock remains, and
 * never lets the count go negative. (The real concurrency guarantee is proven
 * against Oracle in SectorConcurrentReservationIT - this fake only needs to
 * honor the contract for single-threaded use case tests.)
 */
class InMemorySectorRepository implements SectorRepository {

    private final Map<String, Sector> sectors = new HashMap<>();
    private final Map<String, Integer> available = new HashMap<>();

    void add(Sector sector) {
        sectors.put(sector.id(), sector);
        available.put(sector.id(), sector.available());
    }

    int availableOf(String sectorId) {
        return available.get(sectorId);
    }

    @Override
    public Optional<Sector> findById(String sectorId) {
        Sector base = sectors.get(sectorId);
        if (base == null) {
            return Optional.empty();
        }
        return Optional.of(new Sector(base.id(), base.eventId(), base.name(),
                base.priceCents(), base.capacity(), available.get(sectorId)));
    }

    @Override
    public boolean tryReserve(String sectorId, int quantity) {
        Integer current = available.get(sectorId);
        if (current == null || current < quantity) {
            return false;
        }
        available.put(sectorId, current - quantity);
        return true;
    }

    @Override
    public void release(String sectorId, int quantity) {
        available.computeIfPresent(sectorId, (id, current) -> current + quantity);
    }
}
