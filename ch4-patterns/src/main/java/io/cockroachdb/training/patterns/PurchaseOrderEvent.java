package io.cockroachdb.training.patterns;

import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.patterns.outbox.OutboxEvent;

public class PurchaseOrderEvent extends OutboxEvent<PurchaseOrder> {
}
