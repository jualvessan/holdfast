package io.github.jualvessan.holdfast.infrastructure.config;

import io.github.jualvessan.holdfast.application.reservation.CreateReservationUseCase;
import io.github.jualvessan.holdfast.application.reservation.IdGenerator;
import io.github.jualvessan.holdfast.domain.inventory.SectorRepository;
import io.github.jualvessan.holdfast.domain.reservation.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Wires the framework-free application layer into Spring. This is the only
 * place that knows how use cases are assembled.
 */
@Configuration
public class ApplicationConfig {

    /**
     * Ticks in whole milliseconds. The database stores TIMESTAMP with
     * microsecond precision, so a finer clock would make the first response
     * (nanosecond expiry) differ from an idempotent replay read back from the
     * database. Millisecond values survive the round trip unchanged.
     */
    @Bean
    public Clock clock() {
        return Clock.tickMillis(ZoneOffset.UTC);
    }

    @Bean
    public IdGenerator idGenerator() {
        return () -> UUID.randomUUID().toString();
    }

    @Bean
    public CreateReservationUseCase createReservationUseCase(
            ReservationRepository reservations,
            SectorRepository sectors,
            IdGenerator idGenerator,
            Clock clock,
            @Value("${holdfast.reservation.hold-duration:PT10M}") Duration holdDuration) {
        return new CreateReservationUseCase(reservations, sectors, idGenerator, clock, holdDuration);
    }

    @Bean
    public TransactionalCreateReservation transactionalCreateReservation(
            CreateReservationUseCase useCase,
            PlatformTransactionManager transactionManager) {
        return new TransactionalCreateReservation(useCase, transactionManager);
    }
}
