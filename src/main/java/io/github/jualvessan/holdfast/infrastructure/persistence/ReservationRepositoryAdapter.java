package io.github.jualvessan.holdfast.infrastructure.persistence;

import io.github.jualvessan.holdfast.domain.reservation.Reservation;
import io.github.jualvessan.holdfast.domain.reservation.ReservationRepository;
import io.github.jualvessan.holdfast.domain.reservation.ReservationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Oracle implementation of the reservation port.
 *
 * <p>{@link #save} lets the {@code UNIQUE} constraint on {@code idempotency_key}
 * surface as a {@link org.springframework.dao.DuplicateKeyException}. That is
 * intentional: the database, not application code, is the final arbiter when two
 * requests with the same key race. The transactional wrapper turns that
 * exception into a replay of the winner's reservation.
 */
@Repository
public class ReservationRepositoryAdapter implements ReservationRepository {

    private static final String INSERT = """
            INSERT INTO reservation
                   (id, sector_id, quantity, unit_price_cents, status, idempotency_key,
                    request_hash, expires_at, created_at, updated_at, confirmed_at, cancelled_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE reservation
               SET status = ?, updated_at = ?, confirmed_at = ?, cancelled_at = ?
             WHERE id = ?
            """;

    private static final String SELECT_COLUMNS = """
            SELECT id, sector_id, quantity, unit_price_cents, status, idempotency_key,
                   request_hash, expires_at, created_at, updated_at, confirmed_at, cancelled_at
              FROM reservation
            """;

    private static final RowMapper<Reservation> RESERVATION_MAPPER = (rs, rowNum) -> Reservation.rehydrate(
            SqlMapping.fromBytes(rs.getBytes("id")),
            SqlMapping.fromBytes(rs.getBytes("sector_id")),
            rs.getInt("quantity"),
            rs.getLong("unit_price_cents"),
            ReservationStatus.valueOf(rs.getString("status")),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            SqlMapping.getInstant(rs, "expires_at"),
            SqlMapping.getInstant(rs, "created_at"),
            SqlMapping.getInstant(rs, "updated_at"),
            SqlMapping.getInstant(rs, "confirmed_at"),
            SqlMapping.getInstant(rs, "cancelled_at"));

    private final JdbcTemplate jdbc;

    public ReservationRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Reservation save(Reservation reservation) {
        jdbc.update(INSERT, ps -> {
            ps.setBytes(1, SqlMapping.toBytes(reservation.id()));
            ps.setBytes(2, SqlMapping.toBytes(reservation.sectorId()));
            ps.setInt(3, reservation.quantity());
            ps.setLong(4, reservation.unitPriceCents());
            ps.setString(5, reservation.status().name());
            ps.setString(6, reservation.idempotencyKey());
            ps.setString(7, reservation.requestHash());
            SqlMapping.setInstant(ps, 8, reservation.expiresAt());
            SqlMapping.setInstant(ps, 9, reservation.createdAt());
            SqlMapping.setInstant(ps, 10, reservation.updatedAt());
            SqlMapping.setInstant(ps, 11, reservation.confirmedAt().orElse(null));
            SqlMapping.setInstant(ps, 12, reservation.cancelledAt().orElse(null));
        });
        return reservation;
    }

    @Override
    public Reservation update(Reservation reservation) {
        int updatedRows = jdbc.update(UPDATE, ps -> {
            ps.setString(1, reservation.status().name());
            SqlMapping.setInstant(ps, 2, reservation.updatedAt());
            SqlMapping.setInstant(ps, 3, reservation.confirmedAt().orElse(null));
            SqlMapping.setInstant(ps, 4, reservation.cancelledAt().orElse(null));
            ps.setBytes(5, SqlMapping.toBytes(reservation.id()));
        });
        if (updatedRows != 1) {
            throw new IllegalStateException("Reservation [%s] does not exist".formatted(reservation.id()));
        }
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(String id) {
        return SqlMapping.tryToBytes(id).flatMap(key ->
                jdbc.query(SELECT_COLUMNS + " WHERE id = ?", ps -> ps.setBytes(1, key), RESERVATION_MAPPER)
                        .stream()
                        .findFirst());
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query(SELECT_COLUMNS + " WHERE idempotency_key = ?",
                        ps -> ps.setString(1, idempotencyKey), RESERVATION_MAPPER)
                .stream()
                .findFirst();
    }
}
