package io.github.jualvessan.holdfast.domain.inventory;

import java.util.Objects;

/**
 * Represents a sector of an event, with its own limited stock of units.
 *
 * <p>Design note (see ADR-0001): this aggregate intentionally does NOT expose
 * a mutating {@code reserve(quantity)} method that decrements {@code available}
 * in memory. The decision recorded in ADR-0001 is that stock deduction is
 * enforced atomically at the persistence layer, via a single conditional
 * {@code UPDATE} statement - not via the typical "load aggregate, mutate,
 * save" pattern, which would reintroduce the race condition the ADR exists to
 * avoid. {@link SectorRepository#tryReserve(String, int)} is the actual
 * authority on whether a reservation succeeds.
 *
 * <p>This class therefore represents a read-only snapshot of a sector: useful
 * to validate invariants on construction and to answer informational
 * questions (e.g. "is there likely to be availability?"), but never the
 * source of truth for a concurrent sale decision.
 */
public final class Sector {

    private final String id;
    private final String eventId;
    private final String name;
    private final long priceCents;
    private final int capacity;
    private final int available;

    public Sector(String id, String eventId, String name, long priceCents, int capacity, int available) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.priceCents = priceCents;
        this.capacity = capacity;
        this.available = available;
        validateInvariants();
    }

    private void validateInvariants() {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (available < 0 || available > capacity) {
            throw new IllegalArgumentException(
                    "available (%d) must be between 0 and capacity (%d)".formatted(available, capacity));
        }
        if (priceCents < 0) {
            throw new IllegalArgumentException("priceCents must not be negative, got " + priceCents);
        }
    }

    /**
     * Informational only - does not guarantee a subsequent reservation will
     * succeed under concurrency. See the class-level note above.
     */
    public boolean hasAvailability(int quantity) {
        return available >= quantity;
    }

    public String id() {
        return id;
    }

    public String eventId() {
        return eventId;
    }

    public String name() {
        return name;
    }

    public long priceCents() {
        return priceCents;
    }

    public int capacity() {
        return capacity;
    }

    public int available() {
        return available;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sector other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
