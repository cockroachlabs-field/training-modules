package io.cockroachdb.training.contention;

import java.util.Optional;
import java.util.UUID;

import io.cockroachdb.training.domain.model.PurchaseOrder;
import io.cockroachdb.training.domain.model.ShipmentStatus;
import io.cockroachdb.training.domain.model.Simulation;

public interface OrderService {
    Optional<PurchaseOrder> findOrderById(UUID id);

    PurchaseOrder placeOrder(PurchaseOrder order);

    void updateOrder(UUID id, ShipmentStatus preCondition, ShipmentStatus postCondition,
                     Simulation simulation);
}
