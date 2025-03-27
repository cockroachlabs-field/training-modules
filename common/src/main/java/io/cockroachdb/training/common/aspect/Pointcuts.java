package io.cockroachdb.training.common.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import io.cockroachdb.training.common.annotation.Retryable;
import io.cockroachdb.training.common.annotation.TransactionExplicit;
import io.cockroachdb.training.common.annotation.TransactionImplicit;

/**
 * Various domain-agnostic pointcut expression for retry/decoration aspects.
 *
 * @author Kai Niemi
 */
@Aspect
public class Pointcuts {
    /**
     * Pointcut expression matching all transactional boundaries.
     */
    @Pointcut("execution(public * *(..)) "
            + "&& @annotation(transactionExplicit)")
    public void anyExplicitTransactionBoundary(TransactionExplicit transactionExplicit) {
    }

    /**
     * Pointcut expression matching all non-transactional operations.
     */
    @Pointcut("execution(public * *(..)) "
            + "&& @annotation(transactionImplicit)")
    public void anyImplicitTransactionOperation(TransactionImplicit transactionImplicit) {
    }

    /**
     * Pointcut expression matching all retryable operations.
     */
    @Pointcut("execution(public * *(..)) "
            + "&& @annotation(retryable)")
    public void anyRetryableOperation(Retryable retryable) {
    }
}

