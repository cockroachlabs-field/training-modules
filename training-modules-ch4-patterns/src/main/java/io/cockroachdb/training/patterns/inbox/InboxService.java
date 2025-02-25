package io.cockroachdb.training.patterns.inbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import io.cockroachdb.training.common.annotation.TransactionImplicit;
import io.cockroachdb.training.domain.PurchaseOrder;

@Service
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
