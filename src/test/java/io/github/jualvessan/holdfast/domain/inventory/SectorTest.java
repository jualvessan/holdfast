package io.github.jualvessan.holdfast.domain.inventory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectorTest {

    @Test
    void hasAvailabilityReflectsCurrentSnapshot() {
        Sector sector = new Sector("sector-1", "event-1", "Floor", 10_000, 100, 40);

        assertThat(sector.hasAvailability(40)).isTrue();
        assertThat(sector.hasAvailability(41)).isFalse();
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new Sector("sector-1", "event-1", "Floor", 10_000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void rejectsAvailableAboveCapacity() {
        assertThatThrownBy(() -> new Sector("sector-1", "event-1", "Floor", 10_000, 100, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available");
    }

    @Test
    void rejectsNegativeAvailable() {
        assertThatThrownBy(() -> new Sector("sector-1", "event-1", "Floor", 10_000, 100, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available");
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> new Sector("sector-1", "event-1", "Floor", -1, 100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priceCents");
    }
}
