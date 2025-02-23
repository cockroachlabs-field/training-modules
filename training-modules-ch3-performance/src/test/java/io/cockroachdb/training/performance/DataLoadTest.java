package io.cockroachdb.training.performance;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import io.cockroachdb.training.Chapter3Application;
import io.cockroachdb.training.test.AbstractIntegrationTest;
import io.cockroachdb.training.test.TestDoubles;

@SpringBootTest(classes = {Chapter3Application.class})
public class DataLoadTest extends AbstractIntegrationTest {
    @Test
    @Order(1)
    public void deleteTestDta() {
        testDataService.deleteAllData();
    }

    @Test
    @Order(2)
    public void createCustomers() {
        testDataService.createCustomers(256, TestDoubles::newCustomer);
    }

    @Test
    @Order(3)
    public void createProducts() {
        testDataService.createProducts(256, TestDoubles::newProduct);
    }
}
