package io.github.jualvessan.holdfast.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste-assinatura da Fase 1 (ver ADR-0001): valida que a instrução de UPDATE
 * condicional garante, sob concorrência real, que nunca vendemos mais unidades
 * do que a capacidade do setor - sem overselling e sem "perder" vendas válidas.
 *
 * Propositalmente NÃO usa Spring Data / repositório de domínio ainda: este teste
 * nasceu antes do código de domínio (TDD) e valida diretamente o contrato com o
 * banco descrito na ADR-0001. Quando o SectorRepositoryAdapter existir, ele deve
 * apenas encapsular esta mesma instrução - o teste poderá então ser reescrito
 * para usá-lo, sem mudar o que está sendo verificado.
 */

@Testcontainers
class SectorConcurrentReservationIT {

    private static final int SECTOR_CAPACITY = 100;
    private static final int CONCURRENT_REQUESTS = 200;
    private static final int QUANTITY_PER_REQUEST = 1;

    // Pool limitado de propósito: simula um número realista de conexões da
    // aplicação, não 200 conexões simultâneas abertas ao banco.
    private static final int CONNECTION_POOL_SIZE = 20;

    // FREEPDB1 já é o nome padrão do banco pluggable na imagem oracle-free,
    // por isso não chamamos .withDatabaseName(...) aqui (o Testcontainers
    // rejeita explicitamente a tentativa de redefinir para o próprio padrão).
    @Container
    private static final OracleContainer ORACLE = new OracleContainer(
            DockerImageName.parse("gvenzl/oracle-free:23-slim"))
            .withUsername("holdfast")
            .withPassword("holdfast_dev")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static HikariDataSource dataSource;
    private static String eventId;
    private static String sectorId;

    @BeforeAll
    static void setUpDatabaseAndSchema() {
        dataSource = buildDataSource();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        eventId = insertEvent();
        sectorId = insertSector(eventId, SECTOR_CAPACITY);
    }

    @AfterAll
    static void tearDown() {
        dataSource.close();
    }

    @Test
    void deveVenderNoMaximoACapacidadeDoSetorSobConcorrencia() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // A CountDownLatch de largada garante que todas as threads tentem
        // decrementar o estoque o mais próximo possível do mesmo instante,
        // maximizando a contenção real (em vez de disparos escalonados).
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        boolean completed;

        try (ExecutorService executor = Executors.newFixedThreadPool(CONNECTION_POOL_SIZE)) {
            IntStream.range(0, CONCURRENT_REQUESTS).forEach(requestIndex -> executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean reserved = tryDecrementStock(sectorId, QUANTITY_PER_REQUEST);
                    if (reserved) {
                        successCount.incrementAndGet();
                    } else {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            }));

            startLatch.countDown();
            completed = doneLatch.await(30, TimeUnit.SECONDS);
        }

        assertThat(completed)
                .as("todas as %d requisições deveriam concluir em até 30s", CONCURRENT_REQUESTS)
                .isTrue();

        // O núcleo da garantia: nem uma unidade a mais foi vendida.
        assertThat(successCount.get())
                .as("número de reservas confirmadas deve ser exatamente a capacidade do setor")
                .isEqualTo(SECTOR_CAPACITY);

        assertThat(conflictCount.get())
                .as("as demais requisições devem ser rejeitadas por falta de estoque, não perdidas")
                .isEqualTo(CONCURRENT_REQUESTS - SECTOR_CAPACITY);

        assertThat(readAvailable(sectorId))
                .as("estoque final não pode ficar negativo nem sobrar acima do esperado")
                .isZero();
    }

    /**
     * Instrução central da ADR-0001: uma única operação atômica que já embute
     * a condição de disponibilidade. O número de linhas afetadas é a fonte da
     * verdade - não há leitura prévia do valor de 'available' para decidir.
     */
    private static boolean tryDecrementStock(String sectorId, int quantity) throws Exception {
        String sql = """
                UPDATE sector
                   SET available = available - ?
                 WHERE id = HEXTORAW(?)
                   AND available >= ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setString(2, sectorId);
            statement.setInt(3, quantity);

            int rowsUpdated = statement.executeUpdate();
            return rowsUpdated == 1;
        }
    }

    private static int readAvailable(String sectorId) throws Exception {
        String sql = "SELECT available FROM sector WHERE id = HEXTORAW(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sectorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt("available");
            }
        }
    }

    private static String insertEvent() {
        String id = newRawId();
        execute("""
                INSERT INTO event (id, name, venue, starts_at)
                VALUES (HEXTORAW(?), 'Evento de teste', 'Local de teste', SYSTIMESTAMP + 30)
                """, id);
        return id;
    }

    private static String insertSector(String eventId, int capacity) {
        String id = newRawId();
        execute("""
                INSERT INTO sector (id, event_id, name, price_cents, capacity, available)
                VALUES (HEXTORAW(?), HEXTORAW(?), 'Setor de teste', 10000, ?, ?)
                """, id, eventId, capacity, capacity);
        return id;
    }

    private static void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Gera um RAW(16) em hexadecimal (32 chars) para uso com HEXTORAW no lugar de SYS_GUID(). */
    private static String newRawId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static HikariDataSource buildDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ORACLE.getJdbcUrl());
        config.setUsername(ORACLE.getUsername());
        config.setPassword(ORACLE.getPassword());
        config.setMaximumPoolSize(CONNECTION_POOL_SIZE);
        return new HikariDataSource(config);
    }
}