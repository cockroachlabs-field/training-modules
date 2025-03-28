package io.cockroachdb.training.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import io.cockroachdb.training.Chapter3Application;
import io.cockroachdb.training.domain.model.Customer;
import io.cockroachdb.training.domain.model.Product;
import io.cockroachdb.training.domain.model.PurchaseOrder;
import io.cockroachdb.training.domain.test.AbstractIntegrationTest;
import io.cockroachdb.training.domain.util.ProgressMeter;
import io.cockroachdb.training.domain.util.RandomData;

@SpringBootTest(classes = {Chapter3Application.class})
public class QueryTest extends AbstractIntegrationTest {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private OrderService orderService;

    private final int numOrders = 10_000;

    @Order(0)
    @Test
    public void whenStartingTest_thenBuildCatalogIfNeeded() {
        createCustomersAndProducts(100, 100);

        testDataService.withRandomCustomersAndProducts(100, 100,
                (customers, products) -> {
                    Assertions.assertFalse(customers.isEmpty(), "No customers");
                    Assertions.assertFalse(products.isEmpty(), "No products");

                    List<PurchaseOrder> purchaseOrders = new ArrayList<>();

                    IntStream.rangeClosed(1, numOrders).forEach(value -> {
                        Customer customer = RandomData.selectRandom(customers);
                        Product product = RandomData.selectRandom(products);

                        purchaseOrders.add(PurchaseOrder.builder()
                                .withCustomer(customer)
                                .andOrderItem()
                                .withProductId(product.getId())
                                .withProductSku(product.getSku())
                                .withUnitPrice(product.getPrice())
                                .withQuantity(1)
                                .then()
                                .build());
                    });

                    final ProgressMeter progressMeter = new ProgressMeter()
                            .setStartTime(Instant.now())
                            .setTotal(purchaseOrders.size());
                    final AtomicInteger created = new AtomicInteger();

                    orderService.placeOrders(purchaseOrders, 64, batchSize -> {
                        progressMeter.setCurrent(created.addAndGet(batchSize));
                        progressMeter.setLabel(
                                "[%,d orders remain]".formatted(progressMeter.getTotal() - created.get()));
                        progressMeter.printProgressBar();
                    });

                    return null;
                });
    }

    @Order(1)
    @ParameterizedTest
    @ValueSource(ints = {32, 64, 128, 256})
    public void whenUsingAuthoritativeReads_thenExpectSlowerResult(int threads) {
        List<BigDecimal> allResults = runConcurrentlyAndCollect(threads,
                () -> orderService.sumOrderTotals());

        logger.info("First 10 results as follows:");
        allResults.stream().limit(10).forEach(result -> {
            logger.info("" + result);
        });
    }

    @Order(2)
    @ParameterizedTest
    @ValueSource(ints = {32, 64, 128, 256})
    public void whenUsingFollowerReads_thenExpectFasterResult(int threads) {
        List<BigDecimal> allResults = runConcurrentlyAndCollect(threads,
                () -> orderService.sumOrderTotalsHistoricalQuery());

        logger.info("First 10 results as follows:");
        allResults.stream().limit(10).forEach(result -> {
            logger.info("" + result);
        });
    }

    @Order(3)
    @ParameterizedTest
    @ValueSource(ints = {32, 64, 128, 256})
    public void whenUsingFollowerReadsAndImplicitTransactionWithNativeQuery_thenExpectFastestResult(int threads) {
        List<BigDecimal> allResults = runConcurrentlyAndCollect(threads,
                () -> orderService.sumOrderTotalsHistoricalNativeQuery());

        logger.info("First 10 results as follows:");
        allResults.stream().limit(10).forEach(result -> {
            logger.info("" + result);
        });
    }

    private List<BigDecimal> runConcurrentlyAndCollect(int threads, Callable<BigDecimal> task) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        // If this is removed then the virtual threads will queue up, so ensure the connection-timeout value
        // is high enough, or you risk getting timeouts (signalling pool exhaustion).
        executor.setConcurrencyLimit(32);

        List<CompletableFuture<BigDecimal>> futures = new ArrayList<>();

        IntStream.rangeClosed(1, threads).forEach(value ->
                futures.add(executor.submitCompletable(task)));

        try {
            CompletableFuture<List<BigDecimal>> allFutures = CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()));
            return allFutures.join();
        } catch (CompletionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
