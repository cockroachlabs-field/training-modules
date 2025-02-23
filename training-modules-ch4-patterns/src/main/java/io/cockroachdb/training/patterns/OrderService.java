package io.cockroachdb.training.patterns;

import io.cockroachdb.training.domain.PurchaseOrder;

public interface OrderService {
    PurchaseOrder placeOrder(PurchaseOrder order);
}
