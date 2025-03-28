package io.cockroachdb.training.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import io.cockroachdb.training.domain.model.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query(value = "select exists(select 1 from product limit 1)", nativeQuery = true)
    Boolean hasProducts();

    // Full scan
    @Query(value = "select id from product order by random() limit :limit", nativeQuery = true)
    List<UUID> findRandomUniqueIds(@Param("limit") int limit);

    // Not used, for reference
    @Lock(LockModeType.PESSIMISTIC_WRITE) // for update
    @Query("select p from Product p where p.id=:id")
    Optional<Product> getByIdWithPessimisticLock(@Param("id") UUID id);
}
