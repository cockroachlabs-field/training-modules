package io.cockroachdb.training.patterns.inbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;

import io.cockroachdb.training.common.annotation.ServiceFacade;
import io.cockroachdb.training.common.annotation.TransactionImplicit;
import io.cockroachdb.training.domain.model.PurchaseOrder;

@ServiceFacade
public class InboxService {
    @Autowired
    private InboxRepository inboxRepository;

    @TransactionImplicit
    @Retryable
    public PurchaseOrder placeOrder(PurchaseOrder order) {
        inboxRepository.writeEvent(order, "purchase_order");
        return order;
    }
}
