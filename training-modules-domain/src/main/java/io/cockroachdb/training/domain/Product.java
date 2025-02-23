package io.cockroachdb.training.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.NaturalId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "product")
public class Product extends AbstractEntity<UUID> {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Product instance = new Product();

        private Builder() {
        }

        public Builder withRandomUUID() {
            instance.id = UUID.randomUUID();
            return this;
        }

        public Builder withId(UUID id) {
            instance.id = id;
            return this;
        }

        public Builder withName(String name) {
            instance.name = name;
            return this;
        }

        public Builder withSku(String sku) {
            instance.sku = sku;
            return this;
        }

        public Builder withPrice(BigDecimal price) {
            instance.price = price;
            return this;
        }

        public Builder withInventory(int inventory) {
            instance.inventory = inventory;
            return this;
        }

        public Product build() {
            return instance;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Version
    private int version;

    @Column(length = 128, nullable = false)
    private String name;

    @NaturalId
    @Column(length = 12, nullable = false, unique = true)
    private String sku;

    @Column(length = 25, nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int inventory;

    @Override
    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal addPrice(BigDecimal price) {
        this.price = price.add(price);
        return this.price;
    }

    public int addInventoryQuantity(int qty) {
        this.inventory += qty;
        return this.inventory;
    }

    public int getInventory() {
        return inventory;
    }

    public void setInventory(int inventory) {
        this.inventory = inventory;
    }

    @Override
    public String toString() {
        return "Product{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", sku='" + sku + '\'' +
               ", price=" + price +
               ", inventory=" + inventory +
               '}';
    }
}
