package io.cockroachdb.training.util;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

public abstract class AssertUtils {
    private AssertUtils() {
    }

    public static void assertReadOnlyTransaction() {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "Txn is not active");
        Assert.isTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly(), "Txn is not read-only");
    }

    public static void assertReadWriteTransaction() {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "Txn is not active");
        Assert.isTrue(!TransactionSynchronizationManager.isCurrentTransactionReadOnly(), "Txn is read-only");
    }

    public static void assertNoTransaction() {
        Assert.isTrue(!TransactionSynchronizationManager.isActualTransactionActive(), "Txn is active");
    }

}
