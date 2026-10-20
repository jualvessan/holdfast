package io.github.jualvessan.holdfast.application.reservation;

/**
 * Generates identifiers for new domain entities. Exists as its own port
 * (rather than calling {@code UUID.randomUUID()} directly inside the use
 * case) purely for testability: a fake implementation can return
 * predictable ids, which makes use case tests easier to write and read.
 */
public interface IdGenerator {

    String newId();
}
