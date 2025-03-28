package io.cockroachdb.training.performance;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.cockroachdb.training.Chapter3Application;
import io.cockroachdb.training.domain.model.Product;
import io.cockroachdb.training.domain.repository.ProductRepository;
import io.cockroachdb.training.domain.test.AbstractIntegrationTest;
import io.cockroachdb.training.domain.util.StreamUtils;
import static java.sql.Statement.SUCCESS_NO_INFO;

@SpringBootTest(classes = {Chapter3Application.class})
public class BatchUpdateTest extends AbstractIntegrationTest {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;

    private final int numProducts = 1024 * 10;

    @BeforeAll
    public void setupTest() {
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setReadOnly(false);
    }

    @Order(1)
    @Test
    public void whenStartingTest_thenRebuildCatalog() {
        createCustomersAndProducts(0, numProducts);
    }

    @Order(2)
    @ParameterizedTest
    @ValueSource(ints = {128, 256, 512, 768, 1024})
    public void whenUpdateProductsUsingBatchStatements_thenObserveNoBatchUpdates(int batchSize) {
        Assertions.assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        Map<UUID, Product> updatedProducts = new HashMap<>();

        Page<Product> products = productRepository.findAll(Pageable.ofSize(numProducts));

        // This doesn't actually get batched over wire in PSQL (like with INSERT rewrites)
        StreamUtils.chunkedStream(products.stream(), batchSize).forEach(chunk -> {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                Assertions.assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "TX not active");

                int rows[] = jdbcTemplate.batchUpdate("UPDATE product SET inventory=?, price=? WHERE id=?",
                        new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(PreparedStatement ps, int i) throws SQLException {
                                Product product = chunk.get(i);
                                product.addInventoryQuantity(1);
                                product.addPrice(new BigDecimal("1.00"));

                                ps.setInt(1, product.getInventory());
                                ps.setBigDecimal(2, product.getPrice());
                                ps.setObject(3, product.getId());

                                updatedProducts.put(product.getId(), product);
                            }

                            @Override
                            public int getBatchSize() {
                                return chunk.size();
                            }
                        });

                Arrays.stream(rows).sequential().forEach(value -> {
                    Assertions.assertNotEquals(value, SUCCESS_NO_INFO);
                });
                Assertions.assertEquals(chunk.size(), rows.length);
            });
        });

        productRepository.findAllById(updatedProducts.keySet()).forEach(product -> {
            Product p = updatedProducts.get(product.getId());
            Assertions.assertEquals(p.getInventory(), product.getInventory());
            Assertions.assertEquals(p.getPrice(), product.getPrice());
        });
    }

    @Order(3)
    @ParameterizedTest
    @ValueSource(ints = {128, 256, 512, 768, 1024})
    public void whenUpdateProductsUsingArrays_thenObserveBatchUpdates(int batchSize) {
        Assertions.assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        Map<UUID, Product> updatedProducts = new HashMap<>();

        Page<Product> products = productRepository.findAll(Pageable.ofSize(numProducts));

        // This does send a single statement batch over the wire
        StreamUtils.chunkedStream(products.stream(), batchSize).forEach(chunk -> {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                int rows = jdbcTemplate.update(
                        "UPDATE product SET inventory=data_table.new_inventory, price=data_table.new_price "
                        + "FROM "
                        + "(select unnest(?) as id, unnest(?) as new_inventory, unnest(?) as new_price) as data_table "
                        + "WHERE product.id=data_table.id",
                        ps -> {
                            List<UUID> ids = new ArrayList<>();
                            List<Integer> qty = new ArrayList<>();
                            List<BigDecimal> price = new ArrayList<>();

                            chunk.forEach(product -> {
                                ids.add(product.getId());
                                qty.add(product.addInventoryQuantity(1));
                                price.add(product.addPrice(new BigDecimal("1.00")));

                                updatedProducts.put(product.getId(), product);
                            });
                            ps.setArray(1, ps.getConnection()
                                    .createArrayOf("UUID", ids.toArray()));
                            ps.setArray(2, ps.getConnection()
                                    .createArrayOf("BIGINT", qty.toArray()));
                            ps.setArray(3, ps.getConnection()
                                    .createArrayOf("DECIMAL", price.toArray()));
                        });
                Assertions.assertEquals(chunk.size(), rows);
            });
        });

        productRepository.findAllById(updatedProducts.keySet()).forEach(product -> {
            Product p = updatedProducts.get(product.getId());
            Assertions.assertEquals(p.getInventory(), product.getInventory());
            Assertions.assertEquals(p.getPrice(), product.getPrice());
        });
    }
}