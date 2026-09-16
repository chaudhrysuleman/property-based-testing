package com.suleman.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a warehouse order event — similar to what you'd see
 * at Picnic, TGTG, or any logistics/e-commerce backend.
 */
public class OrderEvent {

    private final String orderId;
    private final String customerId;
    private final double amount;
    private final String currency;
    private final Instant timestamp;
    private final OrderStatus status;

    @JsonCreator
    public OrderEvent(
            @JsonProperty("orderId") String orderId,
            @JsonProperty("customerId") String customerId,
            @JsonProperty("amount") double amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("status") OrderStatus status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getTimestamp() { return timestamp; }
    public OrderStatus getStatus() { return status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderEvent that = (OrderEvent) o;
        return Double.compare(amount, that.amount) == 0
                && Objects.equals(orderId, that.orderId)
                && Objects.equals(customerId, that.customerId)
                && Objects.equals(currency, that.currency)
                && Objects.equals(timestamp, that.timestamp)
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, customerId, amount, currency, timestamp, status);
    }

    @Override
    public String toString() {
        return "OrderEvent{orderId='%s', customer='%s', amount=%.2f %s, status=%s}"
                .formatted(orderId, customerId, amount, currency, status);
    }
}
