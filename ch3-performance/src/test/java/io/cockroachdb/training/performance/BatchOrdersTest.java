package io.cockroachdb.training.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.cockroachdb.training.Chapter3Application;
import io.cockroachdb.training.domain.model.Customer;
import io.cockroachdb.training.domain.model.Product;
import io.cockroachdb.training.domain.model.PurchaseOrder;
import io.cockroachdb.training.domain.test.AbstractIntegrationTest;
import io.cockroachdb.training.domain.util.RandomData;

@SpringBootTest(classes = {Chapter3Application.class})
public class BatchOrdersTest extends AbstractIntegrationTest {
    @Autowired
    private OrderService orderService;

    protected final int numProducts = 250;

    protected final int numCustomers = 1000;

    @BeforeAll
    public void setupTest() {
        createCustomersAndProducts(numCustomers, numProducts);
    }

    @Order(1)
    @ParameterizedTest
    @ValueSource(ints = {10, 250, 500, 750, 1000})
    public void whenCreatingSingletonOrders_thenSucceed(int numOrders) {
        testDataService.withRandomCustomersAndProducts(100, 100,
                (customers, products) -> {
                    Assertions.assertFalse(customers.isEmpty(), "No customers");
                    Assertions.assertFalse(products.isEmpty(), "No products");

                    IntStream.rangeClosed(1, numOrders).forEach(value -> {
                        Customer customer = RandomData.selectRandom(customers);
                        Product product = RandomData.selectRandom(products);

                        orderService.placeOrder(PurchaseOrder.builder()
                                .withCustomer(customer)
                                .andOrderItem()
                                .withProductId(product.getId())
                                .withProductSku(product.getSku())
                                .withUnitPrice(product.getPrice())
                                .withQuantity(1)
                                .then()
                                .build());
                    });

                    return null;
                });
    }

    @Order(2)
    @ParameterizedTest
    @ValueSource(ints = {10, 250, 500, 750, 1000})
    public void whenCreatingBatchOrders_thenSucceed(int numOrders) {
        testDataService.withRandomCustomersAndProducts(100, 100,
                (customers, products) -> {
                    Assertions.assertFalse(customers.isEmpty(), "No customers");
                    Assertions.assertFalse(products.isEmpty(), "No products");

                    List<PurchaseOrder> batch = new ArrayList<>();

                    IntStream.rangeClosed(1, numOrders).forEach(value -> {
                        Customer customer = RandomData.selectRandom(customers);
                        Product product = RandomData.selectRandom(products);

                        batch.add(PurchaseOrder.builder()
                                .withCustomer(customer)
                                .andOrderItem()
                                .withProductId(product.getId())
                                .withProductSku(product.getSku())
                                .withUnitPrice(product.getPrice())
                                .withQuantity(1)
                                .then()
                                .build());
                    });

                    orderService.placeOrders(batch, 32, integer -> {
                    });

                    return null;
                });
    }
}
