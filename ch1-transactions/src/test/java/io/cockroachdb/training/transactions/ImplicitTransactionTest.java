package io.cockroachdb.training.transactions;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import io.cockroachdb.training.Chapter1Application;
import io.cockroachdb.training.domain.model.Customer;
import io.cockroachdb.training.domain.model.Product;
import io.cockroachdb.training.domain.test.AbstractIntegrationTest;

@SpringBootTest(classes = {Chapter1Application.class})
public class ImplicitTransactionTest extends AbstractIntegrationTest {
    @Autowired
    private OrderService orderService;

    @BeforeAll
    public void beforeAll() {
        createCustomersAndProducts(100, 100);
    }

    @Order(1)
    @Test
    public void whenListingCustomers_thenExpectImplicitTransactions() {
        Page<Customer> page = orderService.findCustomers(PageRequest.ofSize(10));
        while (page.hasContent()) {
            logger.info("Found %s".formatted(page));
            if (page.hasNext()) {
                page = orderService.findCustomers(page.nextPageable());
            } else {
                break;
            }
        }
    }

    @Order(2)
    @Test
    public void whenListingProducts_thenExpectImplicitTransactions() {
        Page<Product> page = orderService.findProducts(PageRequest.ofSize(10));
        while (page.hasContent()) {
            logger.info("Found %s".formatted(page));
            if (page.hasNext()) {
                page = orderService.findProducts(page.nextPageable());
            } else {
                break;
            }
        }
    }
}
