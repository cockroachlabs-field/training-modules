package io.cockroachdb.training.transactions;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import io.cockroachdb.training.domain.Simulation;
import io.cockroachdb.training.domain.Customer;
import io.cockroachdb.training.domain.Product;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.domain.ShipmentStatus;

public interface OrderService {
    Page<Product> findProducts(Pageable pageable);

    Page<Customer> findCustomers(Pageable pageable);

    Page<PurchaseOrder> findOrders(Pageable pageable);

    Optional<PurchaseOrder> findOrderById(UUID id);

    PurchaseOrder placeOrder(PurchaseOrder order);

    PurchaseOrder placeOrderWithValidation(PurchaseOrder order);

    void updateOrder(UUID id, ShipmentStatus preCondition, ShipmentStatus postCondition,
                     Simulation simulation);
}
