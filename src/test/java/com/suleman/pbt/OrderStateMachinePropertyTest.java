package com.suleman.pbt;

import com.suleman.model.OrderStatus;
import net.jqwik.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * USE CASE 2: State Machine Invariant Testing
 *
 * Invariant: The order state machine must NEVER allow
 * invalid transitions, and terminal states must be truly terminal.
 *
 * This catches:
 *   - Missing guard clauses in switch/case statements
 *   - Backward transitions (e.g., DELIVERED → CREATED)
 *   - Transitions out of terminal states
 *   - Unreachable states
 */
class OrderStateMachinePropertyTest {

    private static final List<OrderStatus> TERMINAL_STATES = List.of(
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED,
            OrderStatus.FAILED
    );

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Terminal states must have ZERO outgoing transitions
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void terminalStates_mustNeverTransition(
            @ForAll("terminalStatuses") OrderStatus terminal,
            @ForAll("allStatuses") OrderStatus target) {

        assertFalse(terminal.canTransitionTo(target),
                "Terminal state %s should NOT transition to %s"
                        .formatted(terminal, target));
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: No state can transition to itself
    // ──────────────────────────────────────────────────────────

    @Property
    void noSelfTransitions(@ForAll("allStatuses") OrderStatus status) {
        assertFalse(status.canTransitionTo(status),
                "Self-transition should be illegal: %s → %s".formatted(status, status));
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: No state can transition backward to CREATED
    // ──────────────────────────────────────────────────────────

    @Property
    void noBackwardTransitionToCreated(@ForAll("nonCreatedStatuses") OrderStatus status) {
        assertFalse(status.canTransitionTo(OrderStatus.CREATED),
                "Backward transition to CREATED should be illegal from %s".formatted(status));
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: The happy path must always be valid
    //  CREATED → CONFIRMED → PICKING → PACKED → DISPATCHED → DELIVERED
    // ──────────────────────────────────────────────────────────

    @Example
    void happyPath_mustBeFullyValid() {
        OrderStatus[] happyPath = {
                OrderStatus.CREATED,
                OrderStatus.CONFIRMED,
                OrderStatus.PICKING,
                OrderStatus.PACKED,
                OrderStatus.DISPATCHED,
                OrderStatus.DELIVERED
        };

        for (int i = 0; i < happyPath.length - 1; i++) {
            assertTrue(happyPath[i].canTransitionTo(happyPath[i + 1]),
                    "Happy path broken at: %s → %s".formatted(happyPath[i], happyPath[i + 1]));
        }
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Every non-terminal state must have at least
    //            one valid outgoing transition (no deadlocks)
    // ──────────────────────────────────────────────────────────

    @Property
    void nonTerminalStates_mustHaveAtLeastOneTransition(
            @ForAll("nonTerminalStatuses") OrderStatus status) {

        boolean hasAtLeastOneTransition = Arrays.stream(OrderStatus.values())
                .anyMatch(status::canTransitionTo);

        assertTrue(hasAtLeastOneTransition,
                "Non-terminal state %s has NO valid transitions — this is a deadlock!"
                        .formatted(status));
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: isTerminal() must be consistent with canTransitionTo()
    // ──────────────────────────────────────────────────────────

    @Property
    void isTerminal_mustBeConsistent(@ForAll("allStatuses") OrderStatus status) {
        boolean hasAnyTransition = Arrays.stream(OrderStatus.values())
                .anyMatch(status::canTransitionTo);

        if (status.isTerminal()) {
            assertFalse(hasAnyTransition,
                    "%s is marked terminal but has outgoing transitions!".formatted(status));
        } else {
            assertTrue(hasAnyTransition,
                    "%s is NOT terminal but has no outgoing transitions!".formatted(status));
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Custom Arbitraries
    // ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<OrderStatus> allStatuses() {
        return Arbitraries.of(OrderStatus.class);
    }

    @Provide
    Arbitrary<OrderStatus> terminalStatuses() {
        return Arbitraries.of(TERMINAL_STATES);
    }

    @Provide
    Arbitrary<OrderStatus> nonCreatedStatuses() {
        return Arbitraries.of(OrderStatus.class)
                .filter(s -> s != OrderStatus.CREATED);
    }

    @Provide
    Arbitrary<OrderStatus> nonTerminalStatuses() {
        return Arbitraries.of(OrderStatus.class)
                .filter(s -> !s.isTerminal());
    }
}
