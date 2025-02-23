package io.cockroachdb.training.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.util.Assert;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Embeddable
public class PurchaseOrderItem {
    public static final class NestedBuilder {
        private final PurchaseOrder.Builder parentBuilder;

        private final Consumer<PurchaseOrderItem> callback;

        private int quantity;

        private BigDecimal unitPrice;

        private UUID productId;

        private String productSku;

        NestedBuilder(PurchaseOrder.Builder parentBuilder, Consumer<PurchaseOrderItem> callback) {
            this.parentBuilder = parentBuilder;
            this.callback = callback;
        }

        public NestedBuilder withQuantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public NestedBuilder withUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public NestedBuilder withProductId(UUID id) {
            this.productId = id;
            return this;
        }

        public NestedBuilder withProductSku(String productSku) {
            this.productSku = productSku;
            return this;
        }

        public PurchaseOrder.Builder then() {
            if (Objects.isNull(productId)) {
                Objects.requireNonNull(productSku);
            }
            if (Objects.isNull(productSku)) {
                Objects.requireNonNull(productId);
            }

            Objects.requireNonNull(unitPrice);
            Assert.isTrue(quantity > 0, "quantity must be > 0");

            Product placeHolder = new Product();
            placeHolder.setId(productId);
            placeHolder.setSku(productSku);

            PurchaseOrderItem orderItem = new PurchaseOrderItem();
            orderItem.product = placeHolder;
            orderItem.unitPrice = this.unitPrice;
            orderItem.quantity = this.quantity;

            callback.accept(orderItem);

            return parentBuilder;
        }
    }

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", updatable = false)
    @Fetch(FetchMode.JOIN)
    private Product product;

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal totalCost() {
        if (unitPrice == null) {
            throw new IllegalStateException("unitPrice is null");
        }
        return unitPrice.multiply(new BigDecimal(quantity));
    }

    @Override
    public String toString() {
        return "OrderItem{" +
               "quantity=" + quantity +
               ", unitPrice=" + unitPrice +
               ", product=" + product +
               '}';
    }
}
