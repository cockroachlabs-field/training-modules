package io.cockroachdb.training.test;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.cockroachdb.training.common.annotation.TransactionExplicit;
import io.cockroachdb.training.common.annotation.TransactionImplicit;
import io.cockroachdb.training.domain.Customer;
import io.cockroachdb.training.domain.Product;
import io.cockroachdb.training.repository.CustomerRepository;
import io.cockroachdb.training.repository.OrderRepository;
import io.cockroachdb.training.repository.ProductRepository;
import io.cockroachdb.training.util.StreamUtils;

@Service
public class TestData {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @TransactionImplicit
    public boolean hasProductData() {
        return this.productRepository.hasProducts();
    }

    @TransactionExplicit
    public void deleteAllData() {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "TX not active");

        orderRepository.deleteAllOrderItems();
        orderRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @TransactionExplicit
    public void createProducts(int numProducts, Supplier<Product> supplier) {
        IntStream.rangeClosed(1, numProducts)
                .forEach(value -> productRepository.save(supplier.get()));
    }

    @TransactionExplicit
    public void createCustomers(int numCustomers, Supplier<Customer> supplier) {
        List<Customer> customers = IntStream.rangeClosed(1, numCustomers)
                .mapToObj(value -> supplier.get())
                .toList();

        StreamUtils.chunkedStream(customers.stream(), 128).forEach(chunk -> {
            customerRepository.saveAll(chunk);
        });
    }

    @TransactionImplicit
    public <T> T withRandomCustomersAndProducts(int customerCount, int productCount,
                                                BiConsumerAction<List<Customer>, List<Product>, T> action) {
        List<Customer> customers = customerRepository.findAllById(
                customerRepository.findRandomUniqueIds(customerCount));
        List<Product> products = productRepository.findAllById(
                productRepository.findRandomUniqueIds(productCount));
        return action.accept(customers, products);
    }
}
