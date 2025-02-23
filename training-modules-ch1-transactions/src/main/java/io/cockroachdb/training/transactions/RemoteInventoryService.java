package io.cockroachdb.training.transactions;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RemoteInventoryService implements InventoryService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void validateProductInventory(UUID id, BigDecimal price, int quantity) {
        // Were slow but always succeed
        try {
            logger.info("Validating product id=%s, price=%s, qty=%d".formatted(id,price,quantity));
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextLong(1000, 5000));
            logger.info("Validated product id=%s, price=%s, qty=%d - all good ٩(^‿^)۶"
                    .formatted(id,price,quantity));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
