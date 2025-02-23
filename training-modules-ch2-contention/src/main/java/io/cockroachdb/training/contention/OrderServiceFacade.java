package io.cockroachdb.training.contention;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import io.cockroachdb.training.common.annotation.TransactionExplicit;
import io.cockroachdb.training.domain.Product;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.domain.ShipmentStatus;
import io.cockroachdb.training.domain.Simulation;
import io.cockroachdb.training.repository.OrderRepository;
import io.cockroachdb.training.repository.ProductRepository;
import io.cockroachdb.training.util.AssertUtils;

/**
 * Business service facade for the order system. This service represents the
 * transaction boundary and gateway to all business functionality such as order
 * placement.
 */
@Service
public class OrderServiceFacade implements OrderService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager em;

    @Override
    @TransactionExplicit(readOnly = true)
    public Optional<PurchaseOrder> findOrderById(UUID id) {
        AssertUtils.assertReadOnlyTransaction();
        return orderRepository.findById(id);
    }

    /**
     * Place a single purchase order.
     *
     * @param order the order detail in detached state
     */
    @TransactionExplicit
    @Override
    public PurchaseOrder placeOrder(PurchaseOrder order) throws BusinessException {
        AssertUtils.assertReadWriteTransaction();

        try {
            // Update product inventories for each line item
            order.getOrderItems().forEach(orderItem -> {
                UUID productId = Objects.requireNonNull(orderItem.getProduct().getId());
                Product product = productRepository.getReferenceById(productId);
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


    @Override
    @TransactionExplicit
    @Retryable(exceptionExpression = "@exceptionClassifier.shouldRetry(#root)", maxAttempts = 5,
            backoff = @Backoff(maxDelay = 15_000, multiplier = 1.5))
    public void updateOrder(UUID id, ShipmentStatus preCondition, ShipmentStatus postCondition,
                            Simulation simulation) {
        AssertUtils.assertReadWriteTransaction();

        if (simulation.getPattern().equals(Simulation.Pattern.READ_MODIFY_WRITE)) {
            updateOrderReadModifyWrite(id, preCondition, postCondition, simulation);
        } else {
            updateOrderWriteOnly(id, preCondition, postCondition, simulation);
        }

        simulation.thinkTime();

        em.flush();
    }

    private void updateOrderReadModifyWrite(UUID id,
                                            ShipmentStatus preCondition,
                                            ShipmentStatus postCondition,
                                            Simulation simulation) {
        TypedQuery<PurchaseOrder> query = em.createNamedQuery("findOrderById", PurchaseOrder.class);
        query.setParameter("id", id);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        query.setLockMode(simulation.getLockModeType());

        PurchaseOrder purchaseOrder = query.getSingleResult();
        if (purchaseOrder == null) {
            throw new ObjectRetrievalFailureException(PurchaseOrder.class, id);
        }

        if (purchaseOrder.getStatus().equals(preCondition)) {
            purchaseOrder.setStatus(postCondition);
            purchaseOrder.setDateUpdated(LocalDateTime.now());
        } else {
            logger.warn("Precondition failed (no-op): %s != %s".formatted(preCondition, postCondition));
        }
    }

    private void updateOrderWriteOnly(UUID id,
                                      ShipmentStatus preCondition,
                                      ShipmentStatus postCondition,
                                      Simulation simulation) {
        Query update = em.createNamedQuery("updateOrderStatusById");
        update.setParameter("id", id);
        update.setParameter("preStatus", preCondition);
        update.setParameter("postStatus", postCondition);
        update.setLockMode(simulation.getLockModeType());

        int rowsAffected = update.executeUpdate();
        if (rowsAffected != 1) {
            logger.warn("Precondition failed (no-op): %s != %s".formatted(preCondition, postCondition));
        }
    }
}
