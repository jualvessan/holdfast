package io.github.jualvessan.holdfast.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

/**
 * Shared base for integration tests that need the real Oracle schema.
 *
 * <p>Singleton container pattern: the container starts once per JVM, when the
 * first subclass loads this class, and every IT class reuses it. Starting
 * Oracle takes about a minute, so one container per class would make the suite
 * painfully slow. Testcontainers' Ryuk sidecar removes the container when the
 * JVM exits, which is why there is no explicit stop.
 *
 * <p>Tests stay independent of each other because each one creates its own
 * event and sector with fresh random ids instead of cleaning tables.
 */
public abstract class OracleTestSupport {

    private static final OracleContainer ORACLE = new OracleContainer(
            DockerImageName.parse("gvenzl/oracle-free:23-slim"))
            .withUsername("holdfast")
            .withPassword("holdfast_dev")
            .withStartupTimeout(Duration.ofMinutes(3));

    protected static final HikariDataSource DATA_SOURCE;

    static {
        ORACLE.start();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ORACLE.getJdbcUrl());
        config.setUsername(ORACLE.getUsername());
        config.setPassword(ORACLE.getPassword());
        config.setMaximumPoolSize(20);
        DATA_SOURCE = new HikariDataSource(config);

        Flyway.configure()
                .dataSource(DATA_SOURCE)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    protected static JdbcTemplate newJdbcTemplate() {
        return new JdbcTemplate(DATA_SOURCE);
    }

    /** Inserts a new event with one sector and returns the sector id (a UUID string). */
    protected static String newSector(int capacity, long priceCents) {
        JdbcTemplate jdbc = newJdbcTemplate();
        byte[] eventId = SqlMapping.toBytes(UUID.randomUUID().toString());
        String sectorId = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO event (id, name, venue, starts_at)
                VALUES (?, 'Test event', 'Test venue', SYSTIMESTAMP + 30)
                """, ps -> ps.setBytes(1, eventId));

        jdbc.update("""
                INSERT INTO sector (id, event_id, name, price_cents, capacity, available)
                VALUES (?, ?, 'Test sector', ?, ?, ?)
                """, ps -> {
            ps.setBytes(1, SqlMapping.toBytes(sectorId));
            ps.setBytes(2, eventId);
            ps.setLong(3, priceCents);
            ps.setInt(4, capacity);
            ps.setInt(5, capacity);
        });
        return sectorId;
    }

    protected static int availableOf(String sectorId) {
        return scalarInt("SELECT available FROM sector WHERE id = ?", SqlMapping.toBytes(sectorId));
    }

    protected static int countReservationsOfSector(String sectorId) {
        return scalarInt("SELECT COUNT(*) FROM reservation WHERE sector_id = ?", SqlMapping.toBytes(sectorId));
    }

    protected static int countReservationsWithKey(String idempotencyKey) {
        return scalarInt("SELECT COUNT(*) FROM reservation WHERE idempotency_key = ?", idempotencyKey);
    }

    private static int scalarInt(String sql, Object parameter) {
        try (Connection connection = DATA_SOURCE.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Test helper query failed: " + sql, e);
        }
    }
}
