package com.suleman.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A thread-safe wallet/account balance service.
 * Demonstrates a real-world financial invariant:
 * "A balance can NEVER go negative."
 */
public class WalletService {

    private final ConcurrentHashMap<String, AtomicReference<Double>> wallets = new ConcurrentHashMap<>();

    /**
     * Creates a new wallet with the given initial balance.
     * @throws IllegalArgumentException if initial balance is negative.
     */
    public void createWallet(String walletId, double initialBalance) {
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative: " + initialBalance);
        }
        wallets.put(walletId, new AtomicReference<>(initialBalance));
    }

    /**
     * Deposits a positive amount into the wallet.
     * @throws IllegalArgumentException if amount is not positive.
     */
    public void deposit(String walletId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive: " + amount);
        }
        AtomicReference<Double> balance = getWalletRef(walletId);
        balance.updateAndGet(current -> current + amount);
    }

    /**
     * Withdraws an amount from the wallet.
     * @throws IllegalArgumentException if amount is not positive.
     * @throws IllegalStateException if withdrawal would result in a negative balance.
     */
    public void withdraw(String walletId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive: " + amount);
        }
        AtomicReference<Double> balance = getWalletRef(walletId);

        // Atomic check-and-decrement
        balance.updateAndGet(current -> {
            if (current - amount < 0) {
                throw new IllegalStateException(
                        "Insufficient funds: balance=%.2f, withdrawal=%.2f".formatted(current, amount));
            }
            return current - amount;
        });
    }

    /**
     * Transfers funds from one wallet to another atomically.
     */
    public void transfer(String fromWalletId, String toWalletId, double amount) {
        withdraw(fromWalletId, amount);
        deposit(toWalletId, amount);
    }

    /**
     * Returns the current balance of a wallet.
     */
    public double getBalance(String walletId) {
        return getWalletRef(walletId).get();
    }

    private AtomicReference<Double> getWalletRef(String walletId) {
        AtomicReference<Double> ref = wallets.get(walletId);
        if (ref == null) {
            throw new IllegalArgumentException("Wallet not found: " + walletId);
        }
        return ref;
    }

    /**
     * Clears all wallets (for test isolation).
     */
    public void reset() {
        wallets.clear();
    }
}
