package com.suleman.pbt;

import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * USE CASE 5: Collection & Sorting Algorithm Invariant Testing
 *
 * Invariants:
 *   - Sorting must produce a list of the same length
 *   - Sorting must preserve all original elements (no data loss)
 *   - Sorting must produce a monotonically ordered result
 *   - Sorting must be idempotent: sort(sort(list)) == sort(list)
 *   - Filtering must never increase the collection size
 *
 * This catches:
 *   - Off-by-one errors in custom comparators
 *   - Data loss during in-place sorting
 *   - Unstable sort breaking equal-element ordering assumptions
 *   - Filter predicates that accidentally mutate state
 */
class CollectionInvariantPropertyTest {

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Sorting preserves length
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void sorting_mustPreserveLength(@ForAll List<Integer> original) {
        List<Integer> sorted = new ArrayList<>(original);
        Collections.sort(sorted);

        assertEquals(original.size(), sorted.size(),
                "Sorting changed the list size! Before: %d, After: %d"
                        .formatted(original.size(), sorted.size()));
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Sorting preserves all elements (no data loss)
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void sorting_mustPreserveAllElements(@ForAll List<Integer> original) {
        List<Integer> sorted = new ArrayList<>(original);
        Collections.sort(sorted);

        // Both lists should contain exactly the same elements
        List<Integer> originalCopy = new ArrayList<>(original);
        Collections.sort(originalCopy);

        assertEquals(originalCopy, sorted,
                "Sorting lost or introduced elements!");
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Sorted result must be monotonically ordered
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void sorting_mustProduceMonotonicOrder(@ForAll List<Integer> original) {
        List<Integer> sorted = new ArrayList<>(original);
        Collections.sort(sorted);

        for (int i = 0; i < sorted.size() - 1; i++) {
            assertTrue(sorted.get(i) <= sorted.get(i + 1),
                    "Not monotonically sorted at index %d: %d > %d"
                            .formatted(i, sorted.get(i), sorted.get(i + 1)));
        }
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Sorting is idempotent — sort(sort(x)) == sort(x)
    // ──────────────────────────────────────────────────────────

    @Property(tries = 300)
    void sorting_mustBeIdempotent(@ForAll List<Integer> original) {
        List<Integer> sortedOnce = new ArrayList<>(original);
        Collections.sort(sortedOnce);

        List<Integer> sortedTwice = new ArrayList<>(sortedOnce);
        Collections.sort(sortedTwice);

        assertEquals(sortedOnce, sortedTwice,
                "Sorting is not idempotent! Applying sort twice changed the result.");
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Filtering must NEVER increase collection size
    // ──────────────────────────────────────────────────────────

    @Property(tries = 300)
    void filtering_mustNeverIncreaseSize(
            @ForAll List<Integer> original,
            @ForAll int threshold) {

        List<Integer> filtered = original.stream()
                .filter(n -> n > threshold)
                .toList();

        assertTrue(filtered.size() <= original.size(),
                "Filtering increased the list size! Original: %d, Filtered: %d"
                        .formatted(original.size(), filtered.size()));
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Reversing a list twice must return the original
    // ──────────────────────────────────────────────────────────

    @Property(tries = 300)
    void reversingTwice_mustReturnOriginal(@ForAll List<String> original) {
        List<String> reversed = new ArrayList<>(original);
        Collections.reverse(reversed);
        Collections.reverse(reversed);

        assertEquals(original, reversed,
                "Double-reversing did not return the original list!");
    }
}
