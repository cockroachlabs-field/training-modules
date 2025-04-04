package io.cockroachdb.training.common.aspect;

import java.util.Objects;

import javax.sql.DataSource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.cockroachdb.training.common.annotation.TimeTravel;
import io.cockroachdb.training.common.annotation.TimeTravelMode;
import io.cockroachdb.training.common.annotation.TransactionExplicit;
import io.cockroachdb.training.common.annotation.TransactionPriority;

/**
 * AOP aspect that sets specific and arbitrary transaction/session variables.
 * <p>
 * The main pre-condition is that there must be an existing transaction in scope.
 * This advice must be applied after the {@link TransactionRetryAspect} if used simultaneously,
 * and the Spring transaction advisor in the call chain.
 * <p>
 * See {@link org.springframework.transaction.annotation.EnableTransactionManagement} for
 * controlling weaving order.
 *
 * @author Kai Niemi
 */
@Aspect
@Order(TransactionDecoratorAspect.PRECEDENCE)
public class TransactionDecoratorAspect {
    /**
     * The precedence at which this advice is ordered by which also controls
     * the order it is invoked in the call chain between a source and target.
     */
    public static final int PRECEDENCE = AdvisorOrder.TRANSACTION_ATTRIBUTES_ADVISOR;

    private final JdbcTemplate jdbcTemplate;

    private final boolean hasEnterpriseLicense;

    public TransactionDecoratorAspect(DataSource dataSource) {
        Assert.notNull(dataSource, "dataSource is null");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.hasEnterpriseLicense = MetadataUtils.hasEnterpriseLicense(dataSource);
    }

    @Around(value = "Pointcuts.anyExplicitTransactionBoundary(transactionExplicit)",
            argNames = "pjp,transactionExplicit")
    public Object doInTransaction(ProceedingJoinPoint pjp, TransactionExplicit transactionExplicit)
            throws Throwable {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                "Expecting active transaction - check advice @Order and @EnableTransactionManagement order: "
                + pjp.getSignature().toShortString());

        // Grab from type if needed (for non-annotated methods)
        if (transactionExplicit == null) {
            transactionExplicit = TransactionRetryAspect.findAnnotation(pjp, TransactionExplicit.class);
        }

        Assert.notNull(transactionExplicit, "No @TransactionBoundary annotation found!?");

        if (!"(empty)".equals(transactionExplicit.applicationName())) {
            jdbcTemplate.update("SET application_name=?", transactionExplicit.applicationName());
        }

        if (!TransactionPriority.NORMAL.equals(transactionExplicit.retryPriority())) {
            jdbcTemplate.execute("SET TRANSACTION PRIORITY "
                                 + transactionExplicit.retryPriority().name());
        } else {
            if (TransactionSynchronizationManager.hasResource(TransactionRetryAspect.RETRY_ASPECT_CALL_COUNT)) {
                Integer numCalls = (Integer) TransactionSynchronizationManager
                        .getResource(TransactionRetryAspect.RETRY_ASPECT_CALL_COUNT);
                // Increase priority on retry
                if (Objects.nonNull(numCalls) && numCalls > 1) {
                    jdbcTemplate.execute("SET TRANSACTION PRIORITY "
                                         + transactionExplicit.retryPriority().name());
                }
            }
        }

        if (!"0s".equals(transactionExplicit.idleTimeout())) {
            jdbcTemplate.update("SET idle_in_transaction_session_timeout=?", transactionExplicit.idleTimeout());
        }

        if (transactionExplicit.readOnly()) {
            jdbcTemplate.execute("SET transaction_read_only=true");
        }

        if (hasEnterpriseLicense) {
            TimeTravel timeTravel = transactionExplicit.timeTravel();
            if (timeTravel.mode().equals(TimeTravelMode.FOLLOWER_READ)) {
                jdbcTemplate.execute("SET TRANSACTION AS OF SYSTEM TIME follower_read_timestamp()");
            } else if (timeTravel.mode().equals(TimeTravelMode.HISTORICAL_READ)) {
                jdbcTemplate.update("SET TRANSACTION AS OF SYSTEM TIME INTERVAL '"
                                    + timeTravel.interval() + "'");
            }
        }

        return pjp.proceed();
    }
}
