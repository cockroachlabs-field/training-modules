package io.cockroachdb.training.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.cockroachdb.training.domain.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>,
        PagingAndSortingRepository<Customer, UUID> {
    // Note: Full scan
    @Query(value = "select id from customer order by random() limit :limit", nativeQuery = true)
    List<UUID> findRandomUniqueIds(@Param("limit") int limit);
}
