package io.cockroachdb.training.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.*;

@Entity
@DynamicUpdate
@Table(name = "purchase_order")
@NamedQuery(name = "findOrderById",
        query = "select po from PurchaseOrder po where po.id=:id")
@NamedQuery(name = "updateOrderStatusById",
        query = "update PurchaseOrder po set po.status=:postStatus where po.id=:id and po.status=:preStatus")
public class PurchaseOrder extends AbstractEntity<UUID> {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;

        private Customer customer;

        private final List<PurchaseOrderItem> orderItems = new ArrayList<>();

        private Builder() {
        }

        public Builder withGeneratedId() {
            this.id = UUID.randomUUID();
            return this;
        }

        public Builder withCustomer(Customer customer) {
            this.customer = customer;
            return this;
        }

        public PurchaseOrderItem.NestedBuilder andOrderItem() {
            return new PurchaseOrderItem.NestedBuilder(this, orderItems::add);
        }

        public PurchaseOrder build() {
            if (this.customer == null) {
                throw new IllegalStateException("Missing customer");
            }
            if (this.orderItems.isEmpty()) {
                throw new IllegalStateException("Empty order");
            }

            PurchaseOrder order = new PurchaseOrder();
            order.id = id;
            order.customer = this.customer;
            order.deliveryAddress = customer.getAddress();
            order.orderItems.addAll(this.orderItems);

            return order;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "total_price", nullable = false, updatable = false)
    private BigDecimal totalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ElementCollection(fetch = FetchType.EAGER) // always cascades
    @CollectionTable(name = "purchase_order_item", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "item_pos")
    private List<PurchaseOrderItem> orderItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25, nullable = false)
    private ShipmentStatus status = ShipmentStatus.placed;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, updatable = false, name = "date_placed")
    private LocalDateTime datePlaced;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, name = "date_updated")
    private LocalDateTime dateUpdated;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "address1",
                    column = @Column(name = "deliv_address1")),
            @AttributeOverride(name = "address2",
                    column = @Column(name = "deliv_address2")),
            @AttributeOverride(name = "city",
                    column = @Column(name = "deliv_city")),
            @AttributeOverride(name = "postcode",
                    column = @Column(name = "deliv_postcode")),
            @AttributeOverride(name = "country",
                    column = @Column(name = "deliv_country"))
    })
    private Address deliveryAddress;

    @Override
    public UUID getId() {
        return id;
    }

    @PrePersist
    public void preCreate() {
        if (datePlaced == null) {
            datePlaced = LocalDateTime.now();
        }
        if (dateUpdated == null) {
            dateUpdated = LocalDateTime.now();
        }
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public void setDateUpdated(LocalDateTime dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getDatePlaced() {
        return datePlaced;
    }

    public LocalDateTime getDateUpdated() {
        return dateUpdated;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<PurchaseOrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }

    public Address getDeliveryAddress() {
        return deliveryAddress;
    }

    public BigDecimal subTotal() {
        BigDecimal subTotal = BigDecimal.ZERO;
        for (PurchaseOrderItem oi : orderItems) {
            subTotal = subTotal.add(oi.totalCost());
        }
        return subTotal;
    }

    @Override
    public String toString() {
        return "Order{" +
               "id=" + id +
               ", totalPrice=" + totalPrice +
               ", customer=" + customer +
               ", orderItems=" + orderItems +
               ", status=" + status +
               ", datePlaced=" + datePlaced +
               ", dateUpdated=" + dateUpdated +
               ", deliveryAddress=" + deliveryAddress +
               '}';
    }
}
