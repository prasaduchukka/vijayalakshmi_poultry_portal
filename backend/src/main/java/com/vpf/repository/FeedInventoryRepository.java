package com.vpf.repository;

import com.vpf.entity.FeedInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface FeedInventoryRepository extends JpaRepository<FeedInventory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FeedInventory i where i.id = 1")
    Optional<FeedInventory> findSingletonForUpdate();
}
