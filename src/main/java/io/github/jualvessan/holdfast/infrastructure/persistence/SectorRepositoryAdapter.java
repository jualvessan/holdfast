package io.github.jualvessan.holdfast.infrastructure.persistence;

import io.github.jualvessan.holdfast.domain.inventory.Sector;
import io.github.jualvessan.holdfast.domain.inventory.SectorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Oracle implementation of the sector port.
 *
 * <p>Plain SQL through {@link JdbcTemplate} on purpose (see ADR-0001): the
 * stock deduction must be one atomic conditional {@code UPDATE}, and an ORM
 * would only hide the statement that the whole concurrency guarantee rests on.
 */
@Repository
public class SectorRepositoryAdapter implements SectorRepository {

    private static final String SELECT_BY_ID = """
            SELECT id, event_id, name, price_cents, capacity, available
              FROM sector
             WHERE id = ?
            """;

    /**
     * The core of ADR-0001. The affected row count is the only source of truth:
     * 1 means there was enough stock and it was deducted, 0 means there was not.
     * There is deliberately no SELECT before it.
     */
    private static final String TRY_RESERVE = """
            UPDATE sector
               SET available = available - ?
             WHERE id = ?
               AND available >= ?
            """;

    /**
     * No upper-bound check here: the CHECK constraint on the table
     * (available <= capacity) rejects a release that would exceed capacity.
     */
    private static final String RELEASE = """
            UPDATE sector
               SET available = available + ?
             WHERE id = ?
            """;

    private static final RowMapper<Sector> SECTOR_MAPPER = (rs, rowNum) -> new Sector(
            SqlMapping.fromBytes(rs.getBytes("id")),
            SqlMapping.fromBytes(rs.getBytes("event_id")),
            rs.getString("name"),
            rs.getLong("price_cents"),
            rs.getInt("capacity"),
            rs.getInt("available"));

    private final JdbcTemplate jdbc;

    public SectorRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Sector> findById(String sectorId) {
        return SqlMapping.tryToBytes(sectorId).flatMap(key ->
                jdbc.query(SELECT_BY_ID, ps -> ps.setBytes(1, key), SECTOR_MAPPER)
                        .stream()
                        .findFirst());
    }

    @Override
    public boolean tryReserve(String sectorId, int quantity) {
        byte[] key = SqlMapping.toBytes(sectorId);
        int updatedRows = jdbc.update(TRY_RESERVE, ps -> {
            ps.setInt(1, quantity);
            ps.setBytes(2, key);
            ps.setInt(3, quantity);
        });
        return updatedRows == 1;
    }

    @Override
    public void release(String sectorId, int quantity) {
        byte[] key = SqlMapping.toBytes(sectorId);
        jdbc.update(RELEASE, ps -> {
            ps.setInt(1, quantity);
            ps.setBytes(2, key);
        });
    }
}
