package io.cockroachdb.training.patterns.inbox;

public interface InboxRepository {
    void writeEvent(Object event, String aggregateType);
}
