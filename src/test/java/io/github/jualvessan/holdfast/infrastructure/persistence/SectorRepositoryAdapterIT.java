package io.github.jualvessan.holdfast.infrastructure.persistence;

import io.github.jualvessan.holdfast.domain.inventory.Sector;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectorRepositoryAdapterIT extends OracleTestSupport {

    private final SectorRepositoryAdapter sectors = new SectorRepositoryAdapter(newJdbcTemplate());

    @Test
    void findsASectorAsASnapshotOfItsCurrentState() {
        String sectorId = newSector(100, 15_000);

        Sector sector = sectors.findById(sectorId).orElseThrow();

        assertThat(sector.id()).isEqualTo(sectorId);
        assertThat(sector.name()).isEqualTo("Test sector");
        assertThat(sector.priceCents()).isEqualTo(15_000);
        assertThat(sector.capacity()).isEqualTo(100);
        assertThat(sector.available()).isEqualTo(100);
    }

    @Test
    void unknownSectorIsEmpty() {
        assertThat(sectors.findById(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void anIdThatIsNotAUuidIsEmptyInsteadOfAnError() {
        assertThat(sectors.findById("not-a-uuid")).isEmpty();
    }

    @Test
    void tryReserveDeductsStockWhenEnoughIsAvailable() {
        String sectorId = newSector(10, 5_000);

        boolean reserved = sectors.tryReserve(sectorId, 4);

        assertThat(reserved).isTrue();
        assertThat(availableOf(sectorId)).isEqualTo(6);
    }

    @Test
    void tryReserveCanTakeExactlyTheRemainingStock() {
        String sectorId = newSector(10, 5_000);

        assertThat(sectors.tryReserve(sectorId, 10)).isTrue();
        assertThat(availableOf(sectorId)).isZero();
    }

    @Test
    void tryReserveRefusesAndChangesNothingWhenStockIsInsufficient() {
        String sectorId = newSector(10, 5_000);

        boolean reserved = sectors.tryReserve(sectorId, 11);

        assertThat(reserved).isFalse();
        assertThat(availableOf(sectorId)).isEqualTo(10);
    }

    @Test
    void releaseGivesStockBack() {
        String sectorId = newSector(10, 5_000);
        sectors.tryReserve(sectorId, 6);

        sectors.release(sectorId, 6);

        assertThat(availableOf(sectorId)).isEqualTo(10);
    }

    @Test
    void theDatabaseRefusesAReleaseThatWouldExceedCapacity() {
        String sectorId = newSector(10, 5_000);

        assertThatThrownBy(() -> sectors.release(sectorId, 1))
                .as("the CHECK constraint available <= capacity must protect the invariant")
                .isInstanceOf(DataAccessException.class);

        assertThat(availableOf(sectorId)).isEqualTo(10);
    }
}
