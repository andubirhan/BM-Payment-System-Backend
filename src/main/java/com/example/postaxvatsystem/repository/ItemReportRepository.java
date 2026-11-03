package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.dto.ItemReportDTO;
import com.example.postaxvatsystem.model.InventoryItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

@org.springframework.stereotype.Repository
public interface ItemReportRepository extends Repository<InventoryItem, Long> {

    @Query("""
        SELECT new com.example.postaxvatsystem.dto.ItemReportDTO(
            item.itemCode,
            item.itemName,
            item.quantityOnHand,
            COALESCE(SUM(CASE WHEN txn.transactionType = com.example.postaxvatsystem.model.TransactionType.SALE
                THEN line.quantity ELSE 0L END), 0L),
            COALESCE(SUM(CASE WHEN txn.transactionType = com.example.postaxvatsystem.model.TransactionType.PURCHASE
                THEN line.quantity ELSE 0L END), 0L),
            COALESCE(SUM(CASE WHEN txn.transactionType = com.example.postaxvatsystem.model.TransactionType.SALE
                THEN line.lineNetAmount ELSE 0.00 END), 0.00),
            COALESCE(SUM(CASE WHEN txn.transactionType = com.example.postaxvatsystem.model.TransactionType.PURCHASE
                THEN line.lineNetAmount ELSE 0.00 END), 0.00),
            COUNT(DISTINCT CASE WHEN txn.transactionType = com.example.postaxvatsystem.model.TransactionType.SALE
                THEN txn.id ELSE NULL END),
            COUNT(DISTINCT CASE WHEN txn.transactionType = com.example.postaxvatsystem.model.TransactionType.PURCHASE
                THEN txn.id ELSE NULL END)
        )
        FROM InventoryItem item
        LEFT JOIN POSTransactionLineItem line
            ON line.itemCode = item.itemCode AND line.inventoryAdjusted = true
        LEFT JOIN line.transaction txn
        GROUP BY item.itemCode, item.itemName, item.quantityOnHand
        ORDER BY item.itemName ASC
        """)
    List<ItemReportDTO> findItemReport();
}
