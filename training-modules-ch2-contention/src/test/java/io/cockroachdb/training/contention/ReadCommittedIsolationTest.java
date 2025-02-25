package io.cockroachdb.training.contention;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.LockModeType;

import io.cockroachdb.training.common.aspect.MetadataUtils;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.domain.ShipmentStatus;
import io.cockroachdb.training.domain.Simulation;

/**
 * Assume there is one existing order with status `placed`. We will read that order and
 * change the status to something else based on a pre-condition status (like a
 * compare-and-set operation), only concurrently in two separate transactions, T1 and T2.
 * <h1>Read-modify-write under RC without locks</h1>
 * <pre>
 * | T1                T2                 T3
 * | r(x,'placed')
 * |                   r(x,'placed')
 * | w(x,'confirmed')
 * |                   w(x,'cancelled')
 * | c
 * |                   c
 * |                                      r(x,'cancelled')    <<< should read 'confirmed' but we lost T1's update !!
 * |                                      c
 * </pre>
 * SQL:
 * <pre>
 * begin; set transaction isolation level read committed; -- T1
 * begin; set transaction isolation level read committed; -- T2
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T1
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T2
 * update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T1
 * update purchase_order set status = 'cancelled' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T2, BLOCKS
 * commit; -- T1. This unblocks T2, so T1's update to 'confirmed' is overwritten (aka a lost update aka last-write-wins)
 * commit; -- T2
 * begin; set transaction isolation level read committed; -- T3
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3
 * commit; -- T3. status is not matching precondition of 'placed'
 * </pre>
 * This is known as a read-write (unrepeatable read) conflict, which is prevented by
 * serializable but allowed in read-committed.
 * <p>
 * Under RC, T1 starts by reading but waits to write and commit, allowing T2 to also read
 * the same key, thus getting blocked on T1's write intent. When T1 later commits, it
 * unblocks T2 that overwrites T1's update. This is the P4 lost update anomaly,
 * allowed under RC. Later in T3, the precondition no longer holds since T1 and T2 committed,
 * leading to a no-op in application code but still with a successful commit.
 * <p>
 * The net effect is that these initially concurrently conflicting transactions will
 * both eventually run to completion but the application invariant is breached since other
 * decisions could have been made on the successful completion of T1.
 * <p>
 * <h1>Read-modify-write under RC with pessimistic locks</h1>
 * <pre>
 * | T1                T2                 T3
 * | r(x,'placed')
 * |                   r(x,'confirmed')
 * | w(x,'confirmed')
 * | c
 * |                   c
 * |                                      r(x,'confirmed')
 * |                                      c
 * </pre>
 * SQL:
 * <pre>
 * begin; set transaction isolation level read committed; -- T1
 * begin; set transaction isolation level read committed; -- T2
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001' FOR UPDATE; -- T1
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001' FOR UPDATE; -- T2, BLOCKS
 * update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T1
 * commit; -- T1. This unblocks T2, which reads T1's update to 'confirmed' that cancels out the update as pre-condition
 * update purchase_order set status = 'cancelled' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T2, no rows affected
 * commit; -- T2
 * begin; set transaction isolation level read committed; -- T3
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3
 * commit; -- T3. status is not matching precondition of 'placed'
 * <p>
 * To prevent the lost update, T1 and T2 acquires a lock at read time using 'FOR UPDATE'. This ensures
 * that T2, when unblocked by T1s commit, will read the committed value of T1 that cancels out its
 * update, preserving the invariant. Thus, is critical to apply application side locks in very
 * common read-modify-write scenarios such as the above.
 * <p>
 * To observe this predictably we'll use two separate transaction with a controllable delay
 * between the read and write + commit operations.
 */
@ActiveProfiles({"domain","verbose","rc"})
public class ReadCommittedIsolationTest extends AbstractIsolationTest {
    @Order(0)
    @Test
    public void whenCheckingIsolationLevel_thenExpectRC() {
        Assertions.assertEquals("READ COMMITTED", MetadataUtils.databaseIsolation(dataSource).toUpperCase());
    }

    @Order(1)
    @Test
    public void givenRC_whenReadModifyWriteConcurrently_thenExpectP4LostUpdate() {
        retryableExceptionClassifier.setEnabled(true);
        transientExceptionRetryListener.clear();

        CompletableFuture<?> t1 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId1,
                    ShipmentStatus.placed,
                    ShipmentStatus.confirmed,
                    Simulation.readModifyWrite().setCommitDelay(Duration.ofSeconds(5)));
        });

        CompletableFuture<?> t2 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId1,
                    ShipmentStatus.placed,
                    ShipmentStatus.cancelled,
                    Simulation.readModifyWrite()
                            .setCommitDelay(Duration.ofSeconds(5)));
        }, CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));

        CompletableFuture.allOf(t1, t2).join();

        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId1).orElseThrow();
        // Should be confirmed, meaning we lost T1's update!
        Assertions.assertEquals(ShipmentStatus.cancelled, purchaseOrder.getStatus());

        Assertions.assertEquals(2, transientExceptionRetryListener.getSuccess());
        Assertions.assertEquals(0, transientExceptionRetryListener.getError());
    }

    @Order(2)
    @Test
    public void givenRCWithLocks_whenReadModifyWriteConcurrently_thenExpectSuccess() {
        retryableExceptionClassifier.setEnabled(true);
        transientExceptionRetryListener.clear();

        CompletableFuture<?> t1 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId2,
                    ShipmentStatus.placed,
                    ShipmentStatus.confirmed,
                    Simulation.readModifyWrite()
                            .setLockModeType(LockModeType.PESSIMISTIC_WRITE)
                            .setCommitDelay(Duration.ofSeconds(5)));
        });

        CompletableFuture<?> t2 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId2,
                    ShipmentStatus.placed,
                    ShipmentStatus.cancelled,
                    Simulation.readModifyWrite()
                            .setLockModeType(LockModeType.PESSIMISTIC_WRITE));
        }, CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));

        CompletableFuture.allOf(t1, t2).join();

        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId2).orElseThrow();
        Assertions.assertEquals(ShipmentStatus.confirmed, purchaseOrder.getStatus());

        Assertions.assertEquals(2, transientExceptionRetryListener.getSuccess());
        Assertions.assertEquals(0, transientExceptionRetryListener.getError());
    }
}
