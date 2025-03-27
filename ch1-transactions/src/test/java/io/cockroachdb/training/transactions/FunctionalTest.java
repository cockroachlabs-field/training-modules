package io.cockroachdb.training.transactions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import io.cockroachdb.training.Chapter1Application;
import io.cockroachdb.training.common.aspect.MetadataUtils;
import io.cockroachdb.training.domain.Customer;
import io.cockroachdb.training.domain.Product;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.domain.PurchaseOrderItem;
import io.cockroachdb.training.domain.ShipmentStatus;
import io.cockroachdb.training.domain.Simulation;
import io.cockroachdb.training.test.AbstractIntegrationTest;

@SpringBootTest(classes = {Chapter1Application.class})
public class FunctionalTest extends AbstractIntegrationTest {
    @Autowired
    private DataSource dataSource;

    private UUID purchaseOrderId;

    @Autowired
    private OrderService orderService;

    @BeforeAll
    public void beforeAll() {
        String isolation = MetadataUtils.databaseIsolation(dataSource);
        Assertions.assertEquals("SERIALIZABLE", isolation.toUpperCase());

        createCustomersAndProducts(10, 10);
    }

    @Order(1)
    @Test
    public void whenPlaceOneOrder_thenExpectSuccess() {
        Page<Product> productPage = orderService.findProducts(PageRequest.ofSize(10));
        Page<Customer> customerPage = orderService.findCustomers(PageRequest.ofSize(10));

        Assertions.assertFalse(customerPage.isEmpty(), "No customers");
        Assertions.assertFalse(productPage.isEmpty(), "No products");

        Product product = productPage.getContent().getFirst();

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .withCustomer(customerPage.getContent().getFirst())
                .andOrderItem()
                .withProductId(product.getId())
                .withProductSku(product.getSku())
                .withUnitPrice(product.getPrice())
                .withQuantity(product.getInventory())
                .then()
                .build();

        this.purchaseOrderId = orderService.placeOrder(purchaseOrder).getId();
    }

    @Order(2)
    @Test
    public void whenReadingOrder_thenExpectStatusUpdated() {
        Page<PurchaseOrder> orderPage = orderService.findOrders(PageRequest.ofSize(10));
        Assertions.assertEquals(1, orderPage.getTotalElements());

        PurchaseOrder purchaseOrder = orderPage.getContent().getFirst();
        Assertions.assertEquals(ShipmentStatus.placed, purchaseOrder.getStatus());
    }

    @Order(3)
    @Test
    public void whenReadingOrderLineProduct_thenExpectInventoryUpdated() {
        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId).orElseThrow();

        List<PurchaseOrderItem> items = purchaseOrder.getOrderItems();
        Assertions.assertEquals(1, items.size());

        PurchaseOrderItem purchaseOrderItem = items.getFirst();
        Assertions.assertEquals(purchaseOrderItem.getUnitPrice()
                        .multiply(new BigDecimal(purchaseOrderItem.getQuantity())),
                purchaseOrder.getTotalPrice());

        Product product = purchaseOrderItem.getProduct();
        Assertions.assertEquals(0, product.getInventory());
    }

    @Order(4)
    @Test
    public void givenZeroInventory_whenPlaceOneOrder_thenExpectFailure() {
        Page<Product> productPage = orderService.findProducts(PageRequest.ofSize(10));
        Page<Customer> customerPage = orderService.findCustomers(PageRequest.ofSize(10));

        Assertions.assertFalse(customerPage.isEmpty(), "No customers");
        Assertions.assertFalse(productPage.isEmpty(), "No products");

        Product product = productPage.getContent().getFirst();

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .withCustomer(customerPage.getContent().getFirst())
                .andOrderItem()
                .withProductId(product.getId())
                .withProductSku(product.getSku())
                .withUnitPrice(product.getPrice())
                .withQuantity(1)
                .then()
                .build();

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> {
            orderService.placeOrder(purchaseOrder);
        });
        Assertions.assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());
    }

    @Order(5)
    @Test
    public void givenOrderStatusPlaced_whenUpdatingToConfirmed_thenExpectNewStatus() {
        orderService.updateOrder(purchaseOrderId, ShipmentStatus.placed, ShipmentStatus.confirmed,
                Simulation.none());

        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId).orElseThrow();
        Assertions.assertEquals(ShipmentStatus.confirmed, purchaseOrder.getStatus());
    }

    @Order(6)
    @Test
    public void givenOrderStatusConfirmed_whenUpdatingToDelivered_thenExpectNewStatus() {
        orderService.updateOrder(purchaseOrderId, ShipmentStatus.confirmed, ShipmentStatus.delivered,
                Simulation.none());

        PurchaseOrder purchaseOrder = orderService.findOrderById(purchaseOrderId).orElseThrow();
        Assertions.assertEquals(ShipmentStatus.delivered, purchaseOrder.getStatus());
    }
}


