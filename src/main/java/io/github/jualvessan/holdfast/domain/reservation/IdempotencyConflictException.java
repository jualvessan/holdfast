package io.github.jualvessan.holdfast.domain.reservation;

import io.github.jualvessan.holdfast.domain.shared.DomainException;

/**
 * Thrown when a request arrives with an {@code Idempotency-Key} that was
 * already used, but with a different request body (different
 * {@code requestHash}). This is NOT a legitimate retry - it's either a bug in
 * the client (reusing a key across unrelated requests) or a key collision.
 *
 * <p>A matching key with a matching hash is the legitimate case (a retry of
 * the exact same request) and does NOT throw this exception - the use case
 * returns the original reservation instead. The web layer translates this
 * exception into {@code 422 Unprocessable Entity}.
 */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key [%s] was already used with a different request body".formatted(idempotencyKey));
    }
}
