package io.cockroachdb.training.transactions;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import io.cockroachdb.training.common.annotation.TransactionImplicit;
import io.cockroachdb.training.domain.Customer;
import io.cockroachdb.training.domain.Product;
import io.cockroachdb.training.domain.PurchaseOrder;
import io.cockroachdb.training.domain.ShipmentStatus;
import io.cockroachdb.training.domain.Simulation;
import io.cockroachdb.training.repository.CustomerRepository;
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
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ObjectProvider<OrderService> objectProvider;

    @PersistenceContext
    private EntityManager em;

    @TransactionImplicit
    @Override
    public Page<Product> findProducts(Pageable pageable) {
        AssertUtils.assertNoTransaction();
        return productRepository.findAll(pageable);
    }

    @TransactionImplicit
    @Override
    public Page<Customer> findCustomers(Pageable pageable) {
        AssertUtils.assertNoTransaction();
        return customerRepository.findAll(pageable);
    }

    @TransactionImplicit
    @Override
    public Page<PurchaseOrder> findOrders(Pageable pageable) {
        AssertUtils.assertNoTransaction();
        return orderRepository.findAll(pageable);
    }

    @Override
    @TransactionExplicit(readOnly = true)
    public Optional<PurchaseOrder> findOrderById(UUID id) {
        AssertUtils.assertReadOnlyTransaction();
        return orderRepository.findById(id);
    }

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

    @TransactionImplicit
    @Override
    public PurchaseOrder placeOrderWithValidation(PurchaseOrder order) throws BusinessException {
        AssertUtils.assertNoTransaction();

        // Pre-validate order item products outside of DB txn scope
        order.getOrderItems().forEach(orderItem -> {
            UUID productId = Objects.requireNonNull(orderItem.getProduct().getId());
            inventoryService.validateProductInventory(productId,
                    orderItem.getUnitPrice(),
                    orderItem.getQuantity());
        });

        OrderService selfProxy = objectProvider.getObject();
        return selfProxy.placeOrder(order);
    }

    @Override
    @TransactionExplicit
    @Retryable(exceptionExpression = "@exceptionClassifier.shouldRetry(#root)", maxAttempts = 5,
            backoff = @Backoff(maxDelay = 15_000, multiplier = 1.5))
    public void updateOrder(UUID id, ShipmentStatus preCondition, ShipmentStatus postCondition,
                            Simulation simulation) {
        AssertUtils.assertReadWriteTransaction();

        if (Objects.equals(simulation.getPattern(), Simulation.Pattern.READ_MODIFY_WRITE)) {
            TypedQuery<PurchaseOrder> query = em.createQuery(
                    "select po from PurchaseOrder po where po.id=:id", PurchaseOrder.class);
            query.setParameter("id", id);
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
            query.setLockMode(simulation.getLockModeType());

            // SELECT .. FOR UPDATE
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
        } else {
            // UPDATE directly, which is also a SELECT .. FOR UPDATE in the read part
            Query update = em.createQuery(
                    "update PurchaseOrder po set po.status=:postStatus where po.id=:id and po.status=:preStatus");
            update.setParameter("id", id);
            update.setParameter("preStatus", preCondition);
            update.setParameter("postStatus", postCondition);
            update.setLockMode(simulation.getLockModeType());

            int rowsAffected = update.executeUpdate();
            if (rowsAffected != 1) {
                logger.warn("Precondition failed (no-op): %s != %s".formatted(preCondition, postCondition));
            }
        }

        simulation.thinkTime();

        em.flush();
    }
}
