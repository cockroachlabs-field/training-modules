package io.cockroachdb.training.common.aspect;

import org.springframework.core.Ordered;

/**
 * Ordering constants for transaction advisors.
 *
 * @author Kai Niemi
 */
public interface AdvisorOrder {
    /**
     * Retry advice should have top priority, before any transaction is created.
     */
    int TRANSACTION_RETRY_ADVISOR = Ordered.LOWEST_PRECEDENCE - 5;

    /**
     * Transaction manager advice must come after any retry advisor.
     */
    int TRANSACTION_MANAGER_ADVISOR = Ordered.LOWEST_PRECEDENCE - 4;

    /**
     * Transaction session attribute advice only make sense within a transaction scope.
     */
    int TRANSACTION_ATTRIBUTES_ADVISOR = Ordered.LOWEST_PRECEDENCE - 3;

    /**
     * Any post business transaction advice, potentially within a transaction scope.
     */
    int CHANGE_FEED_ADVISOR = Ordered.LOWEST_PRECEDENCE - 2;
}
