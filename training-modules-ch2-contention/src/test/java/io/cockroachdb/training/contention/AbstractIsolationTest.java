package io.cockroachdb.training.contention;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.cockroachdb.training.Chapter2Application;
import io.cockroachdb.training.common.retry.TransientExceptionRetryListener;
import io.cockroachdb.training.domain.Product;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.common.retry.TransientExceptionClassifier;
import io.cockroachdb.training.test.AbstractIntegrationTest;

@SpringBootTest(classes = {Chapter2Application.class})
public abstract class AbstractIsolationTest extends AbstractIntegrationTest {
    @Autowired
    protected TransientExceptionClassifier retryableExceptionClassifier;

    @Autowired
    protected TransientExceptionRetryListener transientExceptionRetryListener;

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected OrderService orderService;

    protected UUID purchaseOrderId1;

    protected UUID purchaseOrderId2;

    @BeforeAll
    public void beforeAll() {
        createCustomersAndProducts(10, 10);

        this.purchaseOrderId1 = placeOrder();
        this.purchaseOrderId2 = placeOrder();
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
}
