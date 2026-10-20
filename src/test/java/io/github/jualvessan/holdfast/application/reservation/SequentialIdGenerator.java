package io.github.jualvessan.holdfast.application.reservation;

/** Predictable ids ("reservation-1", "reservation-2", ...) so assertions can name them. */
class SequentialIdGenerator implements IdGenerator {

    private int counter = 0;

    @Override
    public String newId() {
        counter++;
        return "reservation-" + counter;
    }
}
