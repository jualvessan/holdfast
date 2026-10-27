package io.github.jualvessan.holdfast.infrastructure.persistence;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates between domain values and the Oracle column types used in the
 * Flyway migrations.
 *
 * <ul>
 *   <li><b>Ids:</b> the domain uses UUID strings; the tables use {@code RAW(16)}.
 *       The conversion is the big-endian bytes of the UUID, so the hex of a
 *       row's id is the UUID without dashes.</li>
 *   <li><b>Instants:</b> the tables use {@code TIMESTAMP} (no time zone). Values
 *       are always written and read as UTC, so the result never depends on the
 *       JVM's default time zone or on daylight saving changes.</li>
 * </ul>
 */
final class SqlMapping {

    private SqlMapping() {
    }

    static byte[] toBytes(String uuid) {
        UUID value = UUID.fromString(uuid);
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    /**
     * Same as {@link #toBytes(String)}, but a value that is not a UUID yields
     * empty instead of an exception. Such a value cannot exist in the database,
     * so lookups by it simply find nothing.
     */
    static Optional<byte[]> tryToBytes(String uuid) {
        try {
            return Optional.of(toBytes(uuid));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    static String fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong()).toString();
    }

    static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, LocalDateTime.ofInstant(value, ZoneOffset.UTC));
        }
    }

    static Instant getInstant(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
