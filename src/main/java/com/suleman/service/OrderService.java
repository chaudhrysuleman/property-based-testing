package com.suleman.service;

import com.suleman.model.OrderEvent;
import com.suleman.model.OrderStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates an in-memory order processing service.
 * Enforces state machine transitions and idempotency.
 */
public class OrderService {

    private final Map<String, OrderEvent> orders = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * Processes an order event. Enforces:
     * 1. Idempotency — duplicate event IDs are silently ignored.
     * 2. State machine — only valid transitions are accepted.
     *
     * @return true if the event was processed, false if it was a duplicate.
     * @throws IllegalStateException if the transition is invalid.
     */
    public boolean processEvent(String eventId, OrderEvent event) {
        // Idempotency guard: silently skip duplicate events
        if (!processedEventIds.add(eventId)) {
            return false; // Already processed
        }

        OrderEvent existing = orders.get(event.getOrderId());

        if (existing != null) {
            // Validate state machine transition
            if (!existing.getStatus().canTransitionTo(event.getStatus())) {
                throw new IllegalStateException(
                        "Invalid transition: %s → %s for order %s"
                                .formatted(existing.getStatus(), event.getStatus(), event.getOrderId()));
            }
        }

        orders.put(event.getOrderId(), event);
        return true;
    }

    /**
     * Retrieves the current state of an order.
     */
    public Optional<OrderEvent> getOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    /**
     * Returns all orders matching the given status.
     */
    public List<OrderEvent> getOrdersByStatus(OrderStatus status) {
        return orders.values().stream()
                .filter(o -> o.getStatus() == status)
                .toList();
    }

    /**
     * Returns the total count of distinct orders.
     */
    public int getOrderCount() {
        return orders.size();
    }

    /**
     * Clears all state (useful for test isolation).
     */
    public void reset() {
        orders.clear();
        processedEventIds.clear();
    }
}
