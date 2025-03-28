package io.cockroachdb.training.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import io.cockroachdb.training.domain.model.PurchaseOrder;
import io.cockroachdb.training.domain.model.ShipmentStatus;

@Repository
public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    @Query(value = "select sum(po.totalPrice) from PurchaseOrder po where po.status=:status")
    BigDecimal sumOrderTotal(@Param("status") ShipmentStatus status);

    @Query(value = "select sum(po.total_price) from purchase_order po " +
                   "as of system time follower_read_timestamp() " +
                   "where po.status=?1", nativeQuery = true)
    BigDecimal sumOrderTotalNativeQuery(String status);

    // Embeddable type and not an entity
    @Modifying
    @Query(value = "delete from purchase_order_item where 1=1", nativeQuery = true)
    void deleteAllOrderItems();

    // Not used, just for reference
    @Query(value = "select o from PurchaseOrder o "
                   + "where o.status=:status ",
            countQuery = "select count(o.id) from PurchaseOrder o "
                         + "where o.status=:status")
    Page<PurchaseOrder> findByStatus(@Param("status") ShipmentStatus status, Pageable page);

    // Not used, just for reference
    @Modifying
    @Query(value = "update PurchaseOrder po set po.status=:post where po.id=:id and po.status=:pre")
    int updateStatus(UUID id, ShipmentStatus pre, ShipmentStatus post);

    // Not used, just for reference
    @Modifying
    @Query(value = "update PurchaseOrder po set po.status=:post where po.id=:id and po.status=:pre")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    int updateStatusWithLock(UUID id, ShipmentStatus pre, ShipmentStatus post);
}
