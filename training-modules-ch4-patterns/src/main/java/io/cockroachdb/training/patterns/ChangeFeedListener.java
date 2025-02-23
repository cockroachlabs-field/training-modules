package io.cockroachdb.training.patterns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ChangeFeedListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(id = "outbox-demo", topics = "orders", groupId = "training-modules",
            properties = {"spring.json.value.default.type=io.cockroachdb.training.patterns.PurchaseOrderEvent"})
    public void onPurchaseOrderEvent(PurchaseOrderEvent event)
            throws JsonProcessingException {
        logger.info("Received event: {}",
                objectMapper.writer(new DefaultPrettyPrinter())
                        .writeValueAsString(event));
    }
}
