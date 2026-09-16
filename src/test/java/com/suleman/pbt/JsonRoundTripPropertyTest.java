package com.suleman.pbt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.suleman.model.OrderEvent;
import com.suleman.model.OrderStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * USE CASE 1: JSON Serialization Round-Trip Testing
 *
 * Invariant: For ANY valid OrderEvent, serializing to JSON and
 * deserializing back must produce an identical object.
 *
 * This catches:
 *   - Missing @JsonProperty annotations
 *   - Incorrect date/time serialization formats
 *   - Double precision loss during JSON round-trips
 *   - Enum serialization mismatches
 *   - Null handling bugs in constructors
 */
class JsonRoundTripPropertyTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ──────────────────────────────────────────────────────────
    //  Custom Arbitrary: generates random but valid OrderEvents
    // ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<OrderEvent> orderEvents() {
        Arbitrary<String> orderIds = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "ORD-" + s);

        Arbitrary<String> customerIds = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(15)
                .map(s -> "CUST-" + s);

        // Use whole cents to avoid floating-point precision issues in JSON
        Arbitrary<Double> amounts = Arbitraries.doubles()
                .between(0.01, 99999.99)
                .map(d -> Math.round(d * 100.0) / 100.0);

        Arbitrary<String> currencies = Arbitraries.of("EUR", "USD", "GBP", "DKK", "CHF");

        Arbitrary<Instant> timestamps = Arbitraries.longs()
                .between(0, Instant.parse("2030-12-31T23:59:59Z").getEpochSecond())
                .map(Instant::ofEpochSecond);

        Arbitrary<OrderStatus> statuses = Arbitraries.of(OrderStatus.class);

        return Combinators.combine(orderIds, customerIds, amounts, currencies, timestamps, statuses)
                .as(OrderEvent::new);
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: serialize(deserialize(event)) == event
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void jsonRoundTrip_mustPreserveAllFields(@ForAll("orderEvents") OrderEvent original) throws Exception {
        // Serialize to JSON
        String json = mapper.writeValueAsString(original);

        // Deserialize back
        OrderEvent reconstructed = mapper.readValue(json, OrderEvent.class);

        // The round-trip must be lossless
        assertEquals(original, reconstructed,
                "Round-trip failed! JSON was: " + json);
    }

    @Property(tries = 200)
    void jsonRoundTrip_mustProduceValidJson(@ForAll("orderEvents") OrderEvent event) throws Exception {
        String json = mapper.writeValueAsString(event);

        // Must be parseable as a generic JSON node (no corruption)
        assertDoesNotThrow(() -> mapper.readTree(json),
                "Generated invalid JSON: " + json);

        // Must contain the orderId field
        assertTrue(json.contains("orderId"),
                "JSON missing 'orderId' field: " + json);
    }

    @Property(tries = 200)
    void serialization_isIdempotent(@ForAll("orderEvents") OrderEvent event) throws Exception {
        // Serializing twice must produce the exact same JSON string
        String json1 = mapper.writeValueAsString(event);
        String json2 = mapper.writeValueAsString(event);

        assertEquals(json1, json2,
                "Serialization is non-deterministic!");
    }
}
