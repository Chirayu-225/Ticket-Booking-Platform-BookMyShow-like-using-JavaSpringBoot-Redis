package com.ticketbooking.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Side effects that must only happen once the database has actually committed
 * (or must be undone if it didn't): releasing Redis locks and pushing
 * seat-map updates to other browsers. Doing either inside the transaction
 * would let other users see — or act on — state that might still roll back.
 */
public final class AfterCommit {

    private AfterCommit() {}

    /** Runs {@code action} after the current transaction commits; immediately if there is none. */
    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Runs {@code action} if the current transaction ends in anything other than a commit. */
    public static void onRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        action.run();
                    }
                }
            });
        }
    }
}
