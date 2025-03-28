package io.cockroachdb.training.patterns.outbox;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.cockroachdb.training.common.aspect.AdvisorOrder;

@Component
@Aspect
@Order(OutboxAspect.PRECEDENCE)
public class OutboxAspect {
    public static final int PRECEDENCE = AdvisorOrder.CHANGE_FEED_ADVISOR;

    private final OutboxRepository outboxRepository;

    public OutboxAspect(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Pointcut expression matching all outbox event operations.
     */
    @Pointcut("execution(public * *(..)) && @annotation(outboxPayload)")
    public void anyOutboxEventOperation(OutboxOperation outboxPayload) {
    }

    @AfterReturning(pointcut = "anyOutboxEventOperation(outboxOperation)",
            returning = "returnValue", argNames = "returnValue,outboxOperation")
    public void doAfterOutboxOperation(Object returnValue, OutboxOperation outboxOperation) {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                "Expected existing transaction - check advisor @Order");

        outboxRepository.writeEvent(returnValue, outboxOperation.aggregateType());
    }
}

