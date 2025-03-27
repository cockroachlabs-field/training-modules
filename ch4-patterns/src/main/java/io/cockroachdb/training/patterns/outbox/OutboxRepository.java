package io.cockroachdb.training.patterns.outbox;

public interface OutboxRepository {
    void writeEvent(Object event, String aggregateType);
}
