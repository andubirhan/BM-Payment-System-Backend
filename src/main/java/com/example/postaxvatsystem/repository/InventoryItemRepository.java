package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT item FROM InventoryItem item WHERE UPPER(item.itemCode) = UPPER(:itemCode)")
    List<InventoryItem> findByItemCodeForUpdate(@Param("itemCode") String itemCode);

    @Query("SELECT item FROM InventoryItem item WHERE UPPER(item.itemCode) = UPPER(:itemCode)")
    Optional<InventoryItem> findByItemCodeIgnoreCase(@Param("itemCode") String itemCode);

    List<InventoryItem> findAllByOrderByItemNameAsc();
}
