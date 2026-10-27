package io.github.jualvessan.holdfast.infrastructure.config;

import io.github.jualvessan.holdfast.application.dto.CreateReservationCommand;
import io.github.jualvessan.holdfast.application.dto.ReservationResult;
import io.github.jualvessan.holdfast.application.reservation.CreateReservationUseCase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs {@link CreateReservationUseCase} inside one database transaction, so the
 * stock deduction and the reservation insert commit or roll back together. The
 * use case itself stays free of framework annotations.
 *
 * <p><b>Racing requests with the same Idempotency-Key.</b> Two such requests can
 * both pass the use case's "key not seen yet" check. They then serialize on the
 * sector row lock taken by the stock UPDATE. The first one inserts and commits;
 * the second one's insert violates the UNIQUE constraint on
 * {@code idempotency_key}, and its transaction rolls back, giving the stock back.
 * Because the winner has already committed by then, running the use case once
 * more in a fresh transaction finds the winner's reservation and replays it.
 * The client never sees the race.
 *
 * <p>Exactly one retry is made: a second unique violation would mean something
 * other than this race is wrong, and must surface as an error.
 */
public class TransactionalCreateReservation {

    private final CreateReservationUseCase useCase;
    private final TransactionTemplate transactionTemplate;

    public TransactionalCreateReservation(CreateReservationUseCase useCase,
                                          PlatformTransactionManager transactionManager) {
        this.useCase = useCase;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ReservationResult execute(CreateReservationCommand command) {
        try {
            return inNewTransaction(command);
        } catch (DuplicateKeyException lostTheRace) {
            return inNewTransaction(command);
        }
    }

    private ReservationResult inNewTransaction(CreateReservationCommand command) {
        return transactionTemplate.execute(status -> useCase.execute(command));
    }
}
