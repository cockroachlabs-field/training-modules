package io.cockroachdb.training.domain.test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import io.cockroachdb.training.domain.model.Address;
import io.cockroachdb.training.domain.model.Customer;
import io.cockroachdb.training.domain.model.Product;
import io.cockroachdb.training.domain.util.RandomData;

public abstract class TestDoubles {
    private TestDoubles() {
    }

    public static final int DEFAULT_INVENTORY_QUANTITY = 1000;

    public static Product newProduct() {
        return Product.builder()
                .withName("CockroachDB Unleashed - First Edition")
                .withPrice(new BigDecimal("150.00").setScale(2, RoundingMode.UNNECESSARY))
                .withSku(RandomData.randomWord(12)) // pseudo-random
                .withInventory(DEFAULT_INVENTORY_QUANTITY)
                .build();
    }

    public static Customer newCustomer() {
        String fn = RandomData.randomFirstName();
        String ln = RandomData.randomLastName();
        String email = RandomData.randomEmail(fn, ln);

        return Customer.builder()
                .withFirstName(fn)
                .withLastName(ln)
                .withEmail(email)
                .withAddress(newAddress())
                .build();
    }

    public static Address newAddress() {
        return Address.builder()
                .withAddress1(RandomData.randomWord(15))
                .withAddress2(RandomData.randomWord(15))
                .withCity(RandomData.randomCity())
                .withPostcode(RandomData.randomZipCode())
                .withCountry(RandomData.randomCountry())
                .build();
    }
}
