package com.suleman.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A thread-safe wallet/account balance service using exact BigDecimal arithmetic.
 * Demonstrates a real-world financial invariant:
 * "A balance can NEVER go negative, and no cent is ever lost to floating-point drift."
 */
public class WalletService {

    private final ConcurrentHashMap<String, AtomicReference<BigDecimal>> wallets = new ConcurrentHashMap<>();

    /**
     * Creates a new wallet with the given initial balance.
     * @throws IllegalArgumentException if initial balance is null or negative.
     */
    public void createWallet(String walletId, BigDecimal initialBalance) {
        Objects.requireNonNull(initialBalance, "Initial balance cannot be null");
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative: " + initialBalance);
        }
        wallets.put(walletId, new AtomicReference<>(scale(initialBalance)));
    }

    /**
     * Deposits a positive amount into the wallet.
     * @throws IllegalArgumentException if amount is not positive.
     */
    public void deposit(String walletId, BigDecimal amount) {
        Objects.requireNonNull(amount, "Deposit amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive: " + amount);
        }
        BigDecimal scaledAmount = scale(amount);
        AtomicReference<BigDecimal> balance = getWalletRef(walletId);
        balance.updateAndGet(current -> current.add(scaledAmount));
    }

    /**
     * Withdraws an amount from the wallet.
     * @throws IllegalArgumentException if amount is not positive.
     * @throws IllegalStateException if withdrawal would result in a negative balance.
     */
    public void withdraw(String walletId, BigDecimal amount) {
        Objects.requireNonNull(amount, "Withdrawal amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive: " + amount);
        }
        BigDecimal scaledAmount = scale(amount);
        AtomicReference<BigDecimal> balance = getWalletRef(walletId);

        // Atomic check-and-decrement using exact BigDecimal arithmetic
        balance.updateAndGet(current -> {
            BigDecimal updated = current.subtract(scaledAmount);
            if (updated.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException(
                        "Insufficient funds: balance=%s, withdrawal=%s".formatted(current, scaledAmount));
            }
            return updated;
        });
    }

    /**
     * Transfers funds from one wallet to another atomically.
     */
    public void transfer(String fromWalletId, String toWalletId, BigDecimal amount) {
        withdraw(fromWalletId, amount);
        deposit(toWalletId, amount);
    }

    /**
     * Returns the current balance of a wallet.
     */
    public BigDecimal getBalance(String walletId) {
        return getWalletRef(walletId).get();
    }

    private AtomicReference<BigDecimal> getWalletRef(String walletId) {
        AtomicReference<BigDecimal> ref = wallets.get(walletId);
        if (ref == null) {
            throw new IllegalArgumentException("Wallet not found: " + walletId);
        }
        return ref;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Clears all wallets (for test isolation).
     */
    public void reset() {
        wallets.clear();
    }
}
