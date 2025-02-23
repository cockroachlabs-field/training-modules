package io.cockroachdb.training.performance;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.function.Consumer;

import io.cockroachdb.training.domain.PurchaseOrder;

public interface OrderService {
    BigDecimal sumOrderTotals();

    BigDecimal sumOrderTotalsHistoricalQuery();

    BigDecimal sumOrderTotalsHistoricalNativeQuery();

    void placeOrder(PurchaseOrder order);

    void placeOrders(Collection<PurchaseOrder> orders, int batchSize, Consumer<Integer> consumer);

    void placeOrderChunk(Collection<PurchaseOrder> chunk);
}
