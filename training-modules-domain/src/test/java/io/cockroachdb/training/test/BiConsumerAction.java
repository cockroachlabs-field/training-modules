package io.cockroachdb.training.test;

/**
 * Represents an operation that accepts two input arguments (A,B) and returns a
 * third result (C).
 *
 * @see java.util.function.BiConsumer
 */
@FunctionalInterface
public interface BiConsumerAction<A, B, C> {
    C accept(A a, B b);
}
