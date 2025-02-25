package io.cockroachdb.training.patterns.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cockroachdb.training.patterns.OrderService;
import io.cockroachdb.training.patterns.PurchaseOrderEvent;

@Service
public class InboxChangeFeedListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("inboxOrderService")
    private OrderService orderService;

    @KafkaListener(id = "inbox-demo", topics = "orders-inbox", groupId = "training-modules",
            properties = {"spring.json.value.default.type=io.cockroachdb.training.patterns.PurchaseOrderEvent"})
    public void onPurchaseOrderEvent(PurchaseOrderEvent event)
            throws JsonProcessingException {
        logger.info("Received event: {}",
                objectMapper.writer(new DefaultPrettyPrinter())
                        .writeValueAsString(event));
        orderService.placeOrder(event.getPayload());
    }
}
