package io.github.jualvessan.holdfast.domain.shared;

/**
 * Base type for exceptions that represent a violation of a domain invariant or
 * an invalid state transition. Infrastructure (web layer) is responsible for
 * translating these into the appropriate HTTP response - the domain itself
 * knows nothing about HTTP.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
