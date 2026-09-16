# Property-Based Testing in Java with jqwik

A production-grade showcase of **Property-Based Testing (PBT)** for modern Java backend systems using [jqwik](https://jqwik.net/) and JUnit 5.

---

## 🎯 Purpose

In production systems, traditional unit tests only verify the **happy paths and edge cases you anticipate**. But distributed services, financial transactions, and event streams break on the bizarre corner cases nobody thought to write: negative balances, broken CAS loops, out-of-order state transitions, or unicode serialization quirks.

**The purpose of this repository is to:**
1. **Demonstrate invariant-driven testing:** Move from testing isolated examples (`add(2, 3) == 5`) to asserting universal system truths that must hold for **thousands of randomly generated inputs**.
2. **Provide battle-tested patterns for backend engineering:** Address real-world scenarios including state machine transitions, event deduplication/idempotency, financial balance conservation, and JSON serialization.
3. **Showcase automated bug shrinking:** Illustrate how PBT frameworks don't just find edge cases—they automatically reduce failing inputs to their minimal reproducible counterexample.

---

## ⚡ Traditional Testing vs. Property-Based Testing

| Aspect | Traditional Example-Based Testing | Property-Based Testing (jqwik) |
| :--- | :--- | :--- |
| **Input Coverage** | 2–5 hand-picked samples | **Hundreds to thousands** of randomized inputs per test |
| **Edge Cases** | Limited to developer foresight | Exhausts extremes: `0`, `-0.0`, `Double.MIN_VALUE`, empty lists, max bounds |
| **Assertion Strategy** | Expected output for a known input | Mathematical **invariants** that must *always* hold true |
| **Failure Diagnosis** | Large, messy payloads that are hard to isolate | Automated **shrinking** to find the minimal failing example |
| **Regression Safety** | Catches repeated regressions | Catches emergent bugs in complex state interactions |

---

## 🔬 Real-World Use Cases & Invariants

This project implements 5 concrete test suites covering 22 property-based specifications:

### 1. JSON Serialization Round-Trip
* `JsonRoundTripPropertyTest`
* **The Invariant:** `deserialize(serialize(payload)) == payload` for any arbitrary domain model.
* **Bugs Caught:** Missing `@JsonProperty` or `@JsonCreator` annotations, timestamp/timezone serialization truncation, precision loss in floating-point fields, and constructor null-pointer traps.

### 2. State Machine Transitions
* `OrderStateMachinePropertyTest`
* **The Invariant:** Terminal states (`DELIVERED`, `CANCELLED`, `FAILED`) have zero outgoing transitions. No self-transitions, no backward transitions to initial states (`CREATED`), and no deadlocks.
* **Bugs Caught:** Missing switch case guards, illegal workflow shortcuts (e.g., jumping from `CREATED` straight to `DELIVERED`), and silent state corruption.

### 3. Event Processing & Idempotency
* `IdempotencyPropertyTest`
* **The Invariant:** Processing the same event $N$ times produces the exact same system state as processing it once ($f(f(x)) = f(x)$). Duplicate events must never increment entity counts or mutate existing records.
* **Bugs Caught:** Flawed deduplication keys, race conditions during upsert operations, and duplicate billing or inventory reservations.

### 4. Financial Wallet & Conservation of Money
* `WalletInvariantPropertyTest`
* **The Invariant:**
  - Balance must **never** drop below zero under any arbitrary sequence of deposits and withdrawals.
  - Fund transfers must strictly conserve total money: $\text{Balance}_A + \text{Balance}_B = \text{Total}_{\text{initial}}$.
  - Round-trip operations return the wallet to the exact original balance.
* **Bugs Caught:** Broken CAS (Compare-And-Swap) retry loops, object identity reference bugs in atomic updates, and floating-point drift (phantom pennies).

### 5. Collection & Sorting Guarantees
* `CollectionInvariantPropertyTest`
* **The Invariant:** Sorting preserves collection length, preserves exact element frequencies, produces strictly monotonic ordering, and is idempotent ($sort(sort(list)) = sort(list)$). Filtering never increases list size.
* **Bugs Caught:** Non-transitive custom comparators (violating `a < b` and `b < c => a < c`), in-place mutation side-effects, and off-by-one errors.

---

## 🚀 Quick Start

### Prerequisites
- **JDK 17+**
- **Maven 3.8+**

### Run the Test Suite
```bash
# Run all 22 property-based tests
mvn test

# Run a specific invariant test suite
mvn test -Dtest=WalletInvariantPropertyTest

# Run with detailed jqwik seed and execution metrics
mvn test -Dtest=OrderStateMachinePropertyTest -Djqwik.reporting.usejunitplatform=true
```

---

## 💡 Key Takeaways

> **Example-based testing** proves your code works for the inputs you imagined.  
> **Property-based testing** proves whether your code survives the chaos you didn't.

---

## 👤 Author

**Chaudhry Muhammad Suleman**  
*Senior Backend Software Engineer*
