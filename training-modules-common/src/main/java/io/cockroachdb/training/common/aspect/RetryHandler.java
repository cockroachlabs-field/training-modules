package io.cockroachdb.training.common.aspect;

import java.sql.SQLException;
import java.time.Duration;

import org.aspectj.lang.Signature;

@FunctionalInterface
public interface RetryHandler {
    boolean isRetryable(SQLException sqlException);

    default void handleNonTransientException(SQLException sqlException) {

    }

    default void handleTransientException(SQLException sqlException,
                                          int methodCalls,
                                          Signature signature,
                                          long maxBackoff) {

    }

    default void handleTransientExceptionRecovery(SQLException sqlException,
                                                  int methodCalls,
                                                  Signature signature,
                                                  Duration elapsedTime) {

    }
}
