package io.cockroachdb.training.performance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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

@SpringBootTest(classes = {Chapter3Application.class})
public class BatchInsertTest extends AbstractIntegrationTest {
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

    @Order(0)
    @Test
    public void whenStartingTest_thenRebuildCatalog() {
        createCustomersAndProducts(0, numProducts);
    }

    @Order(1)
    @ParameterizedTest
    @ValueSource(ints = {128, 256, 512, 768, 1024})
    public void whenGivenBatchInserts_thenExpectDriverRewrite(int batchSize) throws SQLException {
        Page<Product> products = productRepository.findAll(Pageable.ofSize(numProducts));

        Stream<List<Product>> chunks = StreamUtils.chunkedStream(products.stream(), batchSize);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            chunks.forEach(chunk -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO product (id,inventory,price,name,sku) values (?,?,?,?,?) "
                        + "ON CONFLICT (id) DO NOTHING")) {

                    for (Product product : chunk) {
                        ps.setObject(1, product.getId());
                        ps.setObject(2, product.getInventory());
                        ps.setObject(3, product.getPrice());
                        ps.setObject(4, product.getName());
                        ps.setObject(5, product.getSku());
                        ps.addBatch();
                    }

                    ps.executeBatch();
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    @Order(2)
    @ParameterizedTest
    @ValueSource(ints = {128, 256, 512, 768, 1024})
    public void whenInsertOnConflict_thenObserveBatchUpdates(int batchSize) {
        Assertions.assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        Map<UUID, Product> updatedProducts = new HashMap<>();

        Page<Product> products = productRepository.findAll(Pageable.ofSize(numProducts));

        StreamUtils.chunkedStream(products.stream(), batchSize).forEach(chunk -> {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                int rows = jdbcTemplate.update(
                        "INSERT INTO product (id,inventory,price,name,sku) "
                        + "select unnest(?) as id, "
                        + "       unnest(?) as inventory, "
                        + "       unnest(?) as price, "
                        + "       unnest(?) as name, "
                        + "       unnest(?) as sku "
                        + "ON CONFLICT (id) do nothing",
                        ps -> {
                            List<Integer> qty = new ArrayList<>();
                            List<BigDecimal> price = new ArrayList<>();
                            List<UUID> ids = new ArrayList<>();
                            List<String> name = new ArrayList<>();
                            List<String> sku = new ArrayList<>();

                            chunk.forEach(product -> {
                                qty.add(product.getInventory());
                                price.add(product.getPrice());
                                ids.add(product.getId());
                                name.add(product.getName());
                                sku.add(product.getSku());

                                updatedProducts.put(product.getId(), product);
                            });
                            ps.setArray(1, ps.getConnection()
                                    .createArrayOf("UUID", ids.toArray()));
                            ps.setArray(2, ps.getConnection()
                                    .createArrayOf("BIGINT", qty.toArray()));
                            ps.setArray(3, ps.getConnection()
                                    .createArrayOf("DECIMAL", price.toArray()));
                            ps.setArray(4, ps.getConnection()
                                    .createArrayOf("VARCHAR", name.toArray()));
                            ps.setArray(5, ps.getConnection()
                                    .createArrayOf("VARCHAR", sku.toArray()));
                        });
                Assertions.assertEquals(0, rows);
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
    public void whenUpsertProducts_thenObserveBatchUpdates(int batchSize) {
        Assertions.assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        Map<UUID, Product> updatedProducts = new HashMap<>();

        Page<Product> products = productRepository.findAll(Pageable.ofSize(numProducts));

        StreamUtils.chunkedStream(products.stream(), batchSize).forEach(chunk -> {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                int rows = jdbcTemplate.update(
                        "UPSERT INTO product (id,inventory,price,name,sku) "
                        + "select unnest(?) as id, "
                        + "       unnest(?) as inventory, "
                        + "       unnest(?) as price, "
                        + "       unnest(?) as name, "
                        + "       unnest(?) as sku",
                        ps -> {
                            List<Integer> qty = new ArrayList<>();
                            List<BigDecimal> price = new ArrayList<>();
                            List<UUID> ids = new ArrayList<>();
                            List<String> name = new ArrayList<>();
                            List<String> sku = new ArrayList<>();

                            chunk.forEach(product -> {
                                qty.add(product.getInventory());
                                price.add(product.getPrice());
                                ids.add(product.getId());
                                name.add(product.getName());
                                sku.add(product.getSku());

                                updatedProducts.put(product.getId(), product);
                            });
                            ps.setArray(1, ps.getConnection()
                                    .createArrayOf("UUID", ids.toArray()));
                            ps.setArray(2, ps.getConnection()
                                    .createArrayOf("BIGINT", qty.toArray()));
                            ps.setArray(3, ps.getConnection()
                                    .createArrayOf("DECIMAL", price.toArray()));
                            ps.setArray(4, ps.getConnection()
                                    .createArrayOf("VARCHAR", name.toArray()));
                            ps.setArray(5, ps.getConnection()
                                    .createArrayOf("VARCHAR", sku.toArray()));
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