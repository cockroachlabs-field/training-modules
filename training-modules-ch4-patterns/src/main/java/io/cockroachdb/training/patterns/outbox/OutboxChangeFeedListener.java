package io.cockroachdb.training.patterns.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cockroachdb.training.common.annotation.ServiceFacade;
import io.cockroachdb.training.patterns.PurchaseOrderEvent;

@ServiceFacade
public class OutboxChangeFeedListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(id = "outbox-demo", topics = "orders-outbox", groupId = "training-modules",
            properties = {"spring.json.value.default.type=io.cockroachdb.training.patterns.PurchaseOrderEvent"})
    public void onPurchaseOrderEvent(PurchaseOrderEvent event)
            throws JsonProcessingException {
        logger.info("Received event: {}",
                objectMapper.writer(new DefaultPrettyPrinter())
                        .writeValueAsString(event));
    }
}
