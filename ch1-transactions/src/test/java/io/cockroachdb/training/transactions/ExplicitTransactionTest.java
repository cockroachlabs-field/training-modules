package io.cockroachdb.training.transactions;

import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.cockroachdb.training.Chapter1Application;
import io.cockroachdb.training.domain.model.PurchaseOrder;
import io.cockroachdb.training.domain.test.AbstractIntegrationTest;
import io.cockroachdb.training.domain.util.RandomData;

@SpringBootTest(classes = {Chapter1Application.class})
public class ExplicitTransactionTest extends AbstractIntegrationTest {
    @Autowired
    private OrderService orderService;

    @BeforeAll
    public void beforeAll() {
        createCustomersAndProducts(10, 5);
    }

    @Order(1)
    @Test
    public void whenPlacingTenOrders_thenExpectSuccess() {
        testDataService.withRandomCustomersAndProducts(
                10, 5, (customers, products) -> {
                    IntStream.rangeClosed(1, 10).forEach(x -> {
                        PurchaseOrder.Builder orderBuilder = PurchaseOrder.builder()
                                .withCustomer(RandomData.selectRandom(customers));

                        products.forEach(product ->
                                orderBuilder.andOrderItem()
                                        .withProductId(product.getId())
                                        .withProductSku(product.getSku())
                                        .withUnitPrice(product.getPrice())
                                        .withQuantity(2)
                                        .then()
                        );

                        // Txn boundary
                        orderService.placeOrder(orderBuilder.build());
                    });
                    return null;
                });
    }
}
