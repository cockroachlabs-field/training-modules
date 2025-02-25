package io.cockroachdb.training.transactions;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.cockroachdb.training.Chapter1Application;
import io.cockroachdb.training.common.aspect.MetadataUtils;
import io.cockroachdb.training.common.retry.TransientExceptionClassifier;
import io.cockroachdb.training.common.retry.TransientExceptionRetryListener;
import io.cockroachdb.training.domain.Product;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.domain.ShipmentStatus;
import io.cockroachdb.training.domain.Simulation;
import io.cockroachdb.training.test.AbstractIntegrationTest;

/**
 * Assume there is one existing order with status `placed`. We will read that order and
 * change the status to something else based on a pre-condition status (like a
 * compare-and-set operation), only concurrently in two separate transactions, T1 and T2.
 * <p>
 * This is known as a read-write (unrepeatable read) conflict, which is prevented by
 * serializable but allowed in read-committed.
 * <pre>
 * | T1                T2                 T3
 * | r(x,'placed')
 * |                   r(x,'placed')
 * |                   w(x,'cancelled')
 * |                   c
 * | r(x,'placed')
 * | w(x,'confirmed')
 * | a
 * </pre>
 * SQL:
 * <pre>
 * begin; set transaction isolation level serializable; -- T1
 * begin; set transaction isolation level serializable; -- T2
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T1
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T2
 * update purchase_order set status = 'cancelled' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T2
 * commit; -- T2
 * select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T1, repeats read
 * update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001' and status='placed'; -- T1, prints out "ERROR: restart transaction: TransactionRetryWithProtoRefreshError: WriteTooOldError"
 * abort;  -- T1. There's nothing else we can do, this transaction has failed
 * </pre>
 */
@SpringBootTest(classes = {Chapter1Application.class})
public class TransactionRetryTest extends AbstractIntegrationTest {
    @Autowired
    private TransientExceptionClassifier retryableExceptionClassifier;

    @Autowired
    private TransientExceptionRetryListener transientExceptionRetryListener;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OrderService orderService;

    private UUID purchaseOrderId;

    @BeforeAll
    public void beforeAll() {
        createCustomersAndProducts(10, 10);
        this.purchaseOrderId = placeOrder();
    }

    private UUID placeOrder() {
        return testDataService.withRandomCustomersAndProducts(100, 100,
                (customers, products) -> {
                    Assertions.assertFalse(customers.isEmpty(), "No customers");
                    Assertions.assertFalse(products.isEmpty(), "No products");

                    Product product = products.getFirst();

                    PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                            .withCustomer(customers.getFirst())
                            .andOrderItem()
                            .withProductId(product.getId())
                            .withProductSku(product.getSku())
                            .withUnitPrice(product.getPrice())
                            .withQuantity(1)
                            .then()
                            .build();

                    return orderService.placeOrder(purchaseOrder).getId();
                });
    }

    @Order(0)
    @Test
    public void whenCheckingIsolationLevel_thenExpect1SR() {
        Assertions.assertEquals("SERIALIZABLE", MetadataUtils.databaseIsolation(dataSource).toUpperCase());
    }

    @Order(1)
    @Test
    public void given1SR_whenReadWriteConflict_thenExpectRetriesAndNoAnomalies() {
        retryableExceptionClassifier.setEnabled(true);
        transientExceptionRetryListener.clear();

        CompletableFuture<?> t1 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId,
                    ShipmentStatus.placed,
                    ShipmentStatus.confirmed,
                    Simulation.readModifyWrite()
                            .setCommitDelay(Duration.ofSeconds(5))); // Lets' think for 5s before write+commit
        });

        CompletableFuture<?> t2 = CompletableFuture.runAsync(() -> {
            orderService.updateOrder(purchaseOrderId,
                    ShipmentStatus.placed,
                    ShipmentStatus.cancelled,
                    Simulation.readModifyWrite());
        }, CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)); // Ensure T2 starts after T1 and commits before

        // Expect both T1 and T2 to succeed, which will not happen w/o retries
        CompletableFuture.allOf(t1, t2).join();

        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId).orElseThrow();
        Assertions.assertEquals(ShipmentStatus.cancelled, purchaseOrder.getStatus());

        Assertions.assertEquals(2, transientExceptionRetryListener.getSuccess());
        Assertions.assertEquals(1, transientExceptionRetryListener.getError());
    }
}
