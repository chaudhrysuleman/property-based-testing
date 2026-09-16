package com.suleman.model;

/**
 * Valid state transitions for warehouse order processing.
 *
 * State machine:
 *   CREATED → CONFIRMED → PICKING → PACKED → DISPATCHED → DELIVERED
 *                                                        → FAILED
 *   CREATED → CANCELLED
 *   CONFIRMED → CANCELLED
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    PICKING,
    PACKED,
    DISPATCHED,
    DELIVERED,
    CANCELLED,
    FAILED;

    /**
     * Determines whether transitioning from this status to the target status is valid.
     * This encodes the warehouse order state machine rules.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED    -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED  -> target == PICKING || target == CANCELLED;
            case PICKING    -> target == PACKED;
            case PACKED     -> target == DISPATCHED;
            case DISPATCHED -> target == DELIVERED || target == FAILED;
            // Terminal states — no further transitions allowed
            case DELIVERED, CANCELLED, FAILED -> false;
        };
    }

    /**
     * Returns true if this is a terminal (final) state.
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }
}
