package io.cockroachdb.training.patterns.outbox;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.UUIDDeserializer;
import com.fasterxml.jackson.databind.ser.std.UUIDSerializer;

/**
 * A generic outbox event wrapper for CockroachDB CDC queries with 'bare' envelopes.
 *
 * @param <T> the payload generic type (json serde)
 */
public abstract class OutboxEvent<T> {
    // Correlates with the Kafka event key.
    // Projection into payload 'id' attribute.
    @JsonProperty("aggregate_id")
    @JsonSerialize(using = UUIDSerializer.class)
    @JsonDeserialize(using = UUIDDeserializer.class)
    private UUID aggregateId;

    // When using diff change feeds, provides the CRUD operation type
    @JsonProperty("event_type")
    private EventType eventType;

    protected T payload;

    protected OutboxEvent() {
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
               "eventType=" + eventType +
               ", id=" + aggregateId +
               ", payload=" + payload +
               '}';
    }
}
