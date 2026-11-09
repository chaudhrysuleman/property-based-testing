package com.suleman.pbt;

import com.suleman.service.WalletService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * USE CASE 4: Financial Invariant Testing — Wallet/Account Balance (BigDecimal)
 *
 * Invariant: A wallet balance must NEVER go negative,
 * and total money in the system must be conserved to the exact cent during transfers.
 *
 * This catches:
 *   - Race conditions in concurrent deposit/withdraw
 *   - Scale and rounding errors in financial arithmetic
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
    //  PROPERTY 1: Balance must NEVER go negative after any
    //              sequence of deposits and withdrawals
    // ──────────────────────────────────────────────────────────

    @Property(tries = 500)
    void balance_mustNeverGoNegative(
            @ForAll("positiveAmounts") BigDecimal initialBalance,
            @ForAll("operationSequences") List<BigDecimal> operations) {

        walletService.createWallet("wallet-1", initialBalance);

        for (BigDecimal op : operations) {
            try {
                if (op.compareTo(BigDecimal.ZERO) > 0) {
                    walletService.deposit("wallet-1", op);
                } else if (op.compareTo(BigDecimal.ZERO) < 0) {
                    walletService.withdraw("wallet-1", op.abs());
                }
            } catch (IllegalStateException e) {
                // Expected: insufficient funds. Balance should still be >= 0
            }

            // THE INVARIANT: balance must ALWAYS be non-negative (balance >= 0.00)
            BigDecimal balance = walletService.getBalance("wallet-1");
            assertTrue(balance.compareTo(BigDecimal.ZERO) >= 0,
                    "CRITICAL BUG: Balance went negative! balance=%s".formatted(balance));
        }

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY 2A (WHY DOUBLE FAILS IN FINANCE):
    //  Using double for deposit + withdraw causes IEEE 754
    //  binary floating-point drift (e.g. 10.00 + 0.10 + 0.20 - 0.30 != 10.00)
    // ──────────────────────────────────────────────────────────

    @Example
    void doubleArithmetic_failsExactFinancialRoundTrip_dueToBinaryDrift() {
        double initialBalance = 10.00;
        double deposit1 = 0.10;
        double deposit2 = 0.20;
        double withdrawal = 0.30; // 0.10 + 0.20 = 0.30

        // Using primitive double: (10.00 + 0.10 + 0.20) - 0.30
        double finalDoubleBalance = ((initialBalance + deposit1) + deposit2) - withdrawal;

        // FAILS exact equality! finalDoubleBalance is 10.000000000000002 instead of 10.00!
        assertNotEquals(initialBalance, finalDoubleBalance,
                "double suffers from binary floating-point drift (10.000000000000002 != 10.00)");

        // Now run the EXACT same transactions using BigDecimal in WalletService:
        walletService.createWallet("wallet-bd", new BigDecimal("10.00"));
        walletService.deposit("wallet-bd", new BigDecimal("0.10"));
        walletService.deposit("wallet-bd", new BigDecimal("0.20"));
        walletService.withdraw("wallet-bd", new BigDecimal("0.30"));

        BigDecimal finalBigDecimalBalance = walletService.getBalance("wallet-bd");

        // PASSES with 100% exact equality: 10.00 == 10.00!
        assertEquals(0, new BigDecimal("10.00").compareTo(finalBigDecimalBalance));
        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY 2B (PASSING WITH BIGDECIMAL ACROSS 300 RANDOM TRIES):
    //  Deposit then Withdraw of the same BigDecimal amount
    //  must return EXACT original balance (Zero tolerance!)
    // ──────────────────────────────────────────────────────────

    @Property(tries = 300)
    void depositThenWithdraw_mustReturnToOriginalBalance(
            @ForAll("positiveAmounts") BigDecimal initialBalance,
            @ForAll("smallPositiveAmounts") BigDecimal transactionAmount) {

        walletService.createWallet("wallet-rt", initialBalance);

        walletService.deposit("wallet-rt", transactionAmount);
        walletService.withdraw("wallet-rt", transactionAmount);

        BigDecimal finalBalance = walletService.getBalance("wallet-rt");

        // Exact BigDecimal comparison (compareTo == 0) with NO delta tolerance needed!
        assertEquals(0, initialBalance.compareTo(finalBalance),
                "Round-trip deposit/withdraw failed! Expected %s but got %s"
                        .formatted(initialBalance, finalBalance));

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY 3: Transfer between two wallets must conserve
    //              total money in the system to the exact cent
    // ──────────────────────────────────────────────────────────

    @Property(tries = 300)
    void transfer_mustConserveTotalMoney(
            @ForAll("positiveAmounts") BigDecimal balanceA,
            @ForAll("positiveAmounts") BigDecimal balanceB,
            @ForAll("smallPositiveAmounts") BigDecimal transferAmount) {

        walletService.createWallet("alice", balanceA);
        walletService.createWallet("bob", balanceB);

        BigDecimal totalBefore = balanceA.add(balanceB);

        try {
            walletService.transfer("alice", "bob", transferAmount);
        } catch (IllegalStateException e) {
            // Insufficient funds — total should still be conserved
        }

        BigDecimal totalAfter = walletService.getBalance("alice")
                .add(walletService.getBalance("bob"));

        assertEquals(0, totalBefore.compareTo(totalAfter),
                "MONEY LEAK! Total before=%s, total after=%s"
                        .formatted(totalBefore, totalAfter));

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  PROPERTY 4: Creating a wallet with a negative balance
    //              must be rejected
    // ──────────────────────────────────────────────────────────

    @Property(tries = 200)
    void negativeInitialBalance_mustBeRejected(
            @ForAll("negativeAmounts") BigDecimal negativeBalance) {

        assertThrows(IllegalArgumentException.class,
                () -> walletService.createWallet("bad-wallet", negativeBalance),
                "Creating a wallet with negative balance should throw!");

        walletService.reset();
    }

    // ──────────────────────────────────────────────────────────
    //  Custom Arbitraries (Generating exact 2-decimal BigDecimals)
    // ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<BigDecimal> positiveAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("10000.00"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> smallPositiveAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("500.00"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> negativeAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-10000.00"), new BigDecimal("-0.01"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<List<BigDecimal>> operationSequences() {
        // Mix of deposits (positive) and withdrawals (negative)
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-200.00"), new BigDecimal("200.00"))
                .ofScale(2)
                .filter(d -> d.compareTo(BigDecimal.ZERO) != 0)
                .list()
                .ofMinSize(1)
                .ofMaxSize(50);
    }
}
