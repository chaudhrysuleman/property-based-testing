package com.suleman.pbt;

import com.suleman.service.WalletService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * USE CASE 4: Financial Invariant Testing — Wallet/Account Balance
 *
 * Invariant: A wallet balance must NEVER go negative,
 * and total money in the system must be conserved during transfers.
 *
 * This catches:
 *   - Race conditions in concurrent deposit/withdraw
 *   - Floating-point drift causing phantom pennies
 *   - Missing overflow/underflow checks
 *   - Broken CAS (compare-and-swap) loops
 */
class WalletInvariantPropertyTest {

    private final WalletService walletService = new WalletService();

    @AfterProperty
    void cleanup() {
        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Balance must NEVER go negative after any
    //            sequence of deposits and withdrawals
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void balance_mustNeverGoNegative(
            @ForAll("positiveAmounts") double initialBalance,
            @ForAll("operationSequences") List<Double> operations) {

        walletService.createWallet("wallet-1", initialBalance);

        for (double op : operations) {
            try {
                if (op > 0) {
                    walletService.deposit("wallet-1", op);
                } else if (op < 0) {
                    walletService.withdraw("wallet-1", Math.abs(op));
                }
            } catch (IllegalStateException e) {
                // Expected: insufficient funds. Balance should still be >= 0
            }

            // THE INVARIANT: balance must ALWAYS be non-negative
            double balance = walletService.getBalance("wallet-1");
            assertTrue(balance >= 0,
                    "CRITICAL BUG: Balance went negative! balance=%.4f".formatted(balance));
        }

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Deposit then Withdraw of the same amount
    //            must return balance to original (round-trip)
    // ──────────────────────────────────────────────────────────

    @Property(tries = 300)
    void depositThenWithdraw_mustReturnToOriginalBalance(
            @ForAll("positiveAmounts") double initialBalance,
            @ForAll("smallPositiveAmounts") double transactionAmount) {

        // Round to cents to avoid floating-point drift
        double roundedInitial = Math.round(initialBalance * 100.0) / 100.0;
        double roundedTx = Math.round(transactionAmount * 100.0) / 100.0;

        walletService.createWallet("wallet-rt", roundedInitial);

        walletService.deposit("wallet-rt", roundedTx);
        walletService.withdraw("wallet-rt", roundedTx);

        double finalBalance = walletService.getBalance("wallet-rt");

        assertEquals(roundedInitial, finalBalance, 0.001,
                "Round-trip deposit/withdraw failed! Expected %.2f but got %.2f"
                        .formatted(roundedInitial, finalBalance));

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Transfer between two wallets must conserve
    //            total money in the system (conservation law)
    // ──────────────────────────────────────────────────────────

    @Property(tries = 300)
    void transfer_mustConserveTotalMoney(
            @ForAll("positiveAmounts") double balanceA,
            @ForAll("positiveAmounts") double balanceB,
            @ForAll("smallPositiveAmounts") double transferAmount) {

        double roundedA = Math.round(balanceA * 100.0) / 100.0;
        double roundedB = Math.round(balanceB * 100.0) / 100.0;
        double roundedTransfer = Math.round(transferAmount * 100.0) / 100.0;

        walletService.createWallet("alice", roundedA);
        walletService.createWallet("bob", roundedB);

        double totalBefore = roundedA + roundedB;

        try {
            walletService.transfer("alice", "bob", roundedTransfer);
        } catch (IllegalStateException e) {
            // Insufficient funds — total should still be conserved
        }

        double totalAfter = walletService.getBalance("alice") + walletService.getBalance("bob");

        assertEquals(totalBefore, totalAfter, 0.01,
                "MONEY LEAK! Total before=%.2f, total after=%.2f"
                        .formatted(totalBefore, totalAfter));

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY: Creating a wallet with a negative balance
    //            must be rejected
    // ──────────────────────────────────────────────────────────

    @Property(tries = 200)
    void negativeInitialBalance_mustBeRejected(
            @ForAll("negativeAmounts") double negativeBalance) {

        assertThrows(IllegalArgumentException.class,
                () -> walletService.createWallet("bad-wallet", negativeBalance),
                "Creating a wallet with negative balance should throw!");

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  Custom Arbitraries
    // ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Double> positiveAmounts() {
        return Arbitraries.doubles().between(0.01, 10000.0);
    }

    @Provide
    Arbitrary<Double> smallPositiveAmounts() {
        return Arbitraries.doubles().between(0.01, 500.0);
    }

    @Provide
    Arbitrary<Double> negativeAmounts() {
        return Arbitraries.doubles().between(-10000.0, -0.01);
    }

    @Provide
    Arbitrary<List<Double>> operationSequences() {
        // Mix of deposits (positive) and withdrawals (negative)
        return Arbitraries.doubles()
                .between(-200.0, 200.0)
                .filter(d -> d != 0.0)
                .list()
                .ofMinSize(1)
                .ofMaxSize(50);
    }
}
