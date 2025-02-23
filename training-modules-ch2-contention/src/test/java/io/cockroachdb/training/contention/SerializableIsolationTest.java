package io.cockroachdb.training.contention;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import jakarta.persistence.OptimisticLockException;

import io.cockroachdb.training.common.aspect.MetadataUtils;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.domain.ShipmentStatus;
import io.cockroachdb.training.domain.Simulation;

/**
 * Assume there is one existing order with status `placed`. We will read that order and
 * change the status to something else based on a pre-condition status (like a
 * compare-and-set operation), only concurrently in two separate transactions, T1 and T2.
 * <pre>
 * | T1                T2                 T3
 * | r(x,'placed')
 * |                   r(x,'placed')
 * | w(x,'confirmed')
 * |                   w(x,'cancelled')
 * | c                 a
 * |                                      r(x,'confirmed')
 * |                                      c
 * </pre>
 * SQL:
 * <pre>
 * begin; set transaction isolation level serializable; -- T1
 * begin; set transaction isolation level serializable; -- T2
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T1
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T2
 * update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T1
 * update purchase_order set status = 'cancelled' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T2, BLOCKS
 * commit; -- T1. T2 now prints out "ERROR: restart transaction: TransactionRetryWithProtoRefreshError: WriteTooOldError"
 * abort;  -- T2. There's nothing else we can do, this transaction has failed
 * begin; set transaction isolation level serializable; -- T3
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3
 * commit; -- T3. status is not matching precondition
 * </pre>
 * <p>
 * This is known as a read-write (unrepeatable read) conflict, which is prevented by
 * serializable but allowed in read-committed.
 * <p>
 * Under 1SR, T1 starts by reading but waits to write and commit, allowing T2 to also read
 * the same key, thus getting blocked on T1's write intent. When T1 later commits, it
 * forces T2 to abort due to a serialization conflict. When this happens, the retry
 * mechanism will kick in and retry the failed T2 transaction. When retrying in T3,
 * the precondition no longer holds since T1 committed, leading to a no-op in application
 * code but still with a successful commit.
 * <p>
 * The net effect is that these initially concurrently conflicting transactions will
 * both eventually run to completion and the application invariant is safe-guarded.
 * <p>
 * To observe this predictably we'll use two separate transaction with a controllable delay
 * between the read and write + commit operations.
 */
public class SerializableIsolationTest extends AbstractIsolationTest {
    @Order(0)
    @Test
    public void whenCheckingIsolationLevel_thenExpect1SR() {
        Assertions.assertEquals("SERIALIZABLE", MetadataUtils.databaseIsolation(dataSource).toUpperCase());
    }

    @Order(1)
    @Test
    public void given1SRWithRetries_whenReadModifyWriteConcurrently_thenExpectSuccess() {
        retryableExceptionClassifier.setEnabled(true);
        transientExceptionRetryListener.clear();

        CompletableFuture<?> t1 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId1,
                    ShipmentStatus.placed,
                    ShipmentStatus.confirmed,
                    Simulation.readModifyWrite()
                            .setCommitDelay(Duration.ofSeconds(5))); // Lets' think for 5s before write+commit
        });

        CompletableFuture<?> t2 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId1,
                    ShipmentStatus.placed,
                    ShipmentStatus.cancelled,
                    Simulation.readModifyWrite()
                            .setCommitDelay(Duration.ofSeconds(5))); // Let's wait here also for a predicable outcome
        }, CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)); // Ensure T2 starts after T1

        // Expect both T1 and T2 to succeed, which will not happen w/o retries
        CompletableFuture.allOf(t1, t2).join();

        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId1).orElseThrow();
        Assertions.assertEquals(ShipmentStatus.confirmed, purchaseOrder.getStatus());

        Assertions.assertEquals(2, transientExceptionRetryListener.getSuccess());
        Assertions.assertEquals(1, transientExceptionRetryListener.getError());
    }

    @Order(2)
    @Test
    public void given1SRWithoutRetries_whenReadModifyWriteConcurrently_thenExpectFailure() {
        retryableExceptionClassifier.setEnabled(false);
        transientExceptionRetryListener.clear();

        CompletableFuture<?> t1 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId2,
                    ShipmentStatus.placed,
                    ShipmentStatus.confirmed,
                    Simulation.readModifyWrite()
                            .setCommitDelay(Duration.ofSeconds(5)));
        });

        CompletableFuture<?> t2 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId2,
                    ShipmentStatus.placed,
                    ShipmentStatus.cancelled,
                    Simulation.readModifyWrite()
                            .setCommitDelay(Duration.ofSeconds(5)));
        }, CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));

        // Expect only T1 to succeed since were not catching the transient error
        t1.join();

        CompletionException ex = Assertions.assertThrows(CompletionException.class, t2::join);
        Assertions.assertInstanceOf(OptimisticLockException.class, ex.getCause());

        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId2).orElseThrow();
        Assertions.assertEquals(ShipmentStatus.confirmed, purchaseOrder.getStatus());

        Assertions.assertEquals(1, transientExceptionRetryListener.getSuccess());
        Assertions.assertEquals(1, transientExceptionRetryListener.getError());
    }
}
