package io.cockroachdb.training.patterns.outbox;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Repository
public class OutboxJdbcRepository implements OutboxRepository {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void writeEvent(Object event, String aggregateType) {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                "Expected existing transaction - check advisor @Order");

        try {
            String json = objectMapper.writer().writeValueAsString(event);

            logger.info("Writing outbox event: {}", json);

            jdbcTemplate.update(
                    "INSERT INTO outbox (aggregate_type,payload) VALUES (?,?)",
                    ps -> {
                        ps.setString(1, aggregateType);
                        ps.setObject(2, json);
                    });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing outbox JSON payload", e);
        }
    }
}
