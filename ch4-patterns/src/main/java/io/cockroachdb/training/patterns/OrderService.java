package io.cockroachdb.training.patterns;

import io.cockroachdb.training.domain.model.PurchaseOrder;

public interface OrderService {
    PurchaseOrder placeOrder(PurchaseOrder order);
}
