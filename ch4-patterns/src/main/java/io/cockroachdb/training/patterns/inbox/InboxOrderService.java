package io.cockroachdb.training.patterns.inbox;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Retryable;

import io.cockroachdb.training.common.annotation.ServiceFacade;
import io.cockroachdb.training.common.annotation.TransactionExplicit;
import io.cockroachdb.training.domain.model.Product;
import io.cockroachdb.training.domain.model.PurchaseOrder;
import io.cockroachdb.training.domain.model.ShipmentStatus;
import io.cockroachdb.training.domain.repository.OrderRepository;
import io.cockroachdb.training.domain.repository.ProductRepository;
import io.cockroachdb.training.domain.util.AssertUtils;
import io.cockroachdb.training.patterns.BusinessException;
import io.cockroachdb.training.patterns.OrderService;

@ServiceFacade
public class InboxOrderService implements OrderService {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    @TransactionExplicit
    @Retryable
    public PurchaseOrder placeOrder(PurchaseOrder order) throws BusinessException {
        AssertUtils.assertReadWriteTransaction();

        try {
            // Update product inventories for each line item
            order.getOrderItems().forEach(orderItem -> {
                Product product = productRepository.getReferenceById(
                        Objects.requireNonNull(orderItem.getProduct().getId()));
                product.addInventoryQuantity(-orderItem.getQuantity());
            });

            order.setStatus(ShipmentStatus.placed);
            order.setTotalPrice(order.subTotal());

            orderRepository.saveAndFlush(order); // flush to surface any constraint violations

            return order;
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Constraint violation", e);
        }
    }
}
