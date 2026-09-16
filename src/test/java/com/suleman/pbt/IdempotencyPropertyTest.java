package com.suleman.pbt;

import com.suleman.model.OrderEvent;
import com.suleman.model.OrderStatus;
import com.suleman.service.OrderService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * USE CASE 3: Event Processing Idempotency Testing
 *
 * Invariant: Processing the same event twice must NEVER
 * create duplicate orders or corrupt state.
 *
 * This catches:
 *   - Missing deduplication logic
 *   - Race conditions in concurrent event processing
 *   - Incorrect "upsert" behavior
 *   - Off-by-one errors in event counting
 */
class IdempotencyPropertyTest {

    private final OrderService service = new OrderService();

    @BeforeTry
    void beforeEachTry() {
        service.reset();
    }

    @AfterProperty
    void cleanup() {
        service.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Processing the same event N times must have
    //            the same effect as processing it once
    // ──────────────────────────────────────────────────────────

    @Property(tries = 200)
    void duplicateEvents_mustBeIdempotent(
            @ForAll("randomEventIds") String eventId,
            @ForAll("createdOrderEvents") OrderEvent event) {

        // First processing — must succeed
        boolean firstResult = service.processEvent(eventId, event);
        assertTrue(firstResult, "First processing should succeed");

        // Verify the order was stored
        var storedAfterFirst = service.getOrder(event.getOrderId());
        assertTrue(storedAfterFirst.isPresent());

        int countAfterFirst = service.getOrderCount();

        // Process the SAME event again — must be silently ignored
        boolean secondResult = service.processEvent(eventId, event);
        assertFalse(secondResult, "Duplicate event should return false");

        // Third time for good measure
        boolean thirdResult = service.processEvent(eventId, event);
        assertFalse(thirdResult, "Triplicate event should return false");

        // Order count must NOT have changed
        assertEquals(countAfterFirst, service.getOrderCount(),
                "Duplicate events must NOT create additional orders!");

        // The stored order must be identical to the first processing
        assertEquals(storedAfterFirst.get(), service.getOrder(event.getOrderId()).get(),
                "Duplicate events must NOT modify the existing order!");
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Different event IDs for the same order
    //            must update the order (not be treated as duplicates)
    // ──────────────────────────────────────────────────────────

    @Property(tries = 200)
    void differentEventIds_sameOrder_mustUpdateState() {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        OrderEvent created = new OrderEvent(
                orderId, "CUST-001", 50.0, "EUR", Instant.now(), OrderStatus.CREATED);
        OrderEvent confirmed = new OrderEvent(
                orderId, "CUST-001", 50.0, "EUR", Instant.now(), OrderStatus.CONFIRMED);

        // Process creation with event ID "evt-1"
        assertTrue(service.processEvent("evt-create-" + orderId, created));
        assertEquals(OrderStatus.CREATED, service.getOrder(orderId).get().getStatus());

        // Process confirmation with a DIFFERENT event ID "evt-2"
        assertTrue(service.processEvent("evt-confirm-" + orderId, confirmed));
        assertEquals(OrderStatus.CONFIRMED, service.getOrder(orderId).get().getStatus());

        // Order count should still be 1 (same orderId, updated state)
        assertEquals(1, service.getOrderCount());

        // Clean up for next property iteration
        service.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Invalid state transitions must be rejected
    //            even with unique event IDs
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void invalidTransitions_mustThrowException() {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        OrderEvent created = new OrderEvent(
                orderId, "CUST-001", 25.0, "EUR", Instant.now(), OrderStatus.CREATED);

        // Jump directly from CREATED to DELIVERED (skipping intermediate states)
        OrderEvent invalidJump = new OrderEvent(
                orderId, "CUST-001", 25.0, "EUR", Instant.now(), OrderStatus.DELIVERED);

        service.processEvent("evt-create-" + orderId, created);

        assertThrows(IllegalStateException.class,
                () -> service.processEvent("evt-invalid-" + orderId, invalidJump),
                "CREATED → DELIVERED should be rejected by the state machine!");

        // Clean up for next property iteration
        service.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  Custom Arbitraries
    // ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> randomEventIds() {
        return Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(5).ofMaxLength(20)
                .map(s -> "evt-" + s);
    }

    @Provide
    Arbitrary<OrderEvent> createdOrderEvents() {
        return Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(3).ofMaxLength(10)
                .map(id -> new OrderEvent(
                        "ORD-" + id,
                        "CUST-" + id,
                        42.50,
                        "EUR",
                        Instant.now(),
                        OrderStatus.CREATED
                ));
    }
}
