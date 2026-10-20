package io.github.jualvessan.holdfast.application.reservation;

import io.github.jualvessan.holdfast.application.dto.CreateReservationCommand;
import io.github.jualvessan.holdfast.application.dto.ReservationResult;
import io.github.jualvessan.holdfast.domain.inventory.InsufficientStockException;
import io.github.jualvessan.holdfast.domain.inventory.Sector;
import io.github.jualvessan.holdfast.domain.inventory.SectorNotFoundException;
import io.github.jualvessan.holdfast.domain.inventory.SectorRepository;
import io.github.jualvessan.holdfast.domain.reservation.IdempotencyConflictException;
import io.github.jualvessan.holdfast.domain.reservation.Reservation;
import io.github.jualvessan.holdfast.domain.reservation.ReservationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Creates a temporary hold (PENDING reservation) on a sector's stock.
 *
 * <p>Order of operations matters here:
 * <ol>
 *   <li><b>Idempotency check first.</b> A retry of an already-processed request
 *       must return the original result without touching stock again.</li>
 *   <li><b>Load the sector</b> (needed for the unit price, and to answer 404 vs 409
 *       correctly). The loaded snapshot is NOT used to decide whether stock is
 *       available - see the next step.</li>
 *   <li><b>Atomic stock deduction</b> via {@link SectorRepository#tryReserve}. This
 *       single conditional UPDATE is the only authority on whether the sale
 *       happens (ADR-0001).</li>
 *   <li><b>Persist the reservation.</b></li>
 * </ol>
 *
 * <p><b>Transaction boundary:</b> steps 3 and 4 must commit or roll back together.
 * If two requests with the same idempotency key race past step 1, the second
 * insert violates the unique constraint on {@code idempotency_key}; the
 * resulting exception must roll back that request's stock deduction. This class
 * is intentionally free of framework annotations - the transactional wrapper is
 * applied from the infrastructure layer, keeping this class testable with plain
 * in-memory fakes.
 */
public class CreateReservationUseCase {

    private final ReservationRepository reservationRepository;
    private final SectorRepository sectorRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration holdDuration;

    public CreateReservationUseCase(ReservationRepository reservationRepository,
                                    SectorRepository sectorRepository,
                                    IdGenerator idGenerator,
                                    Clock clock,
                                    Duration holdDuration) {
        this.reservationRepository = reservationRepository;
        this.sectorRepository = sectorRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.holdDuration = holdDuration;
    }

    public ReservationResult execute(CreateReservationCommand command) {
        Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), command);
        }

        Sector sector = sectorRepository.findById(command.sectorId())
                .orElseThrow(() -> new SectorNotFoundException(command.sectorId()));

        if (!sectorRepository.tryReserve(command.sectorId(), command.quantity())) {
            throw new InsufficientStockException(command.sectorId(), command.quantity());
        }

        Instant now = clock.instant();
        Reservation reservation = Reservation.createPending(
                idGenerator.newId(),
                sector.id(),
                command.quantity(),
                sector.priceCents(),
                command.idempotencyKey(),
                command.requestHash(),
                now.plus(holdDuration),
                now);

        return toResult(reservationRepository.save(reservation));
    }

    /**
     * Same key + same body = legitimate retry, return the original result.
     * Same key + different body = client bug or key collision, reject.
     */
    private ReservationResult replayOrReject(Reservation existing, CreateReservationCommand command) {
        if (!existing.requestHash().equals(command.requestHash())) {
            throw new IdempotencyConflictException(command.idempotencyKey());
        }
        return toResult(existing);
    }

    private static ReservationResult toResult(Reservation reservation) {
        return new ReservationResult(
                reservation.id(),
                reservation.sectorId(),
                reservation.quantity(),
                reservation.unitPriceCents(),
                reservation.status(),
                reservation.expiresAt());
    }
}
