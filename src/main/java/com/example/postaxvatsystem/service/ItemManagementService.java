package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.exception.InsufficientStockException;
import com.example.postaxvatsystem.model.InventoryItem;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.POSTransactionLineItem;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.dto.ItemReportDTO;
import com.example.postaxvatsystem.repository.InventoryItemRepository;
import com.example.postaxvatsystem.repository.ItemReportRepository;
import com.example.postaxvatsystem.repository.POSTransactionLineItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemManagementService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemReportRepository itemReportRepository;
    private final POSTransactionLineItemRepository lineItemRepository;

    @Transactional
    public void applySuccessfulTransaction(POSTransaction transaction) {
        TransactionType type = transaction.getTransactionType();
        if (type != TransactionType.SALE && type != TransactionType.PURCHASE) {
            return;
        }

        List<POSTransactionLineItem> lineItems = transaction.getLineItems();
        if (lineItems == null || lineItems.isEmpty()) {
            lineItems = lineItemRepository.findByTransactionId(transaction.getId());
        }
        if (lineItems.isEmpty()) {
            return;
        }

        long direction = type == TransactionType.SALE ? -1L : 1L;
        for (POSTransactionLineItem lineItem : lineItems) {
            if (Boolean.TRUE.equals(lineItem.getInventoryAdjusted())) {
                continue;
            }
            if (lineItem.getItemCode() == null || lineItem.getItemCode().isBlank()) {
                throw new IllegalStateException("Cannot adjust inventory for a line item without an item code");
            }
            if (lineItem.getQuantity() == null || lineItem.getQuantity() <= 0) {
                throw new IllegalStateException("Cannot adjust inventory for an invalid line item quantity");
            }

            String normalizedItemCode = lineItem.getItemCode().trim().toUpperCase();
            List<InventoryItem> existingItems = inventoryItemRepository.findByItemCodeForUpdate(normalizedItemCode);
            
            InventoryItem item;
            if (existingItems.isEmpty()) {
                item = createInventoryItem(lineItem);
            } else if (existingItems.size() == 1) {
                item = existingItems.get(0);
            } else {
                // Handle duplicate item codes - merge them
                log.warn("Found {} duplicate inventory items with code {}. Merging them.", 
                    existingItems.size(), normalizedItemCode);
                item = mergeDuplicateItems(existingItems, lineItem);
            }
            
            // For sales, check if item exists and has sufficient stock
            if (type == TransactionType.SALE && item.getId() != null) {
                long quantityOnHand = item.getQuantityOnHand() == null ? 0L : item.getQuantityOnHand();
                if (quantityOnHand < lineItem.getQuantity()) {
                    throw new InsufficientStockException(lineItem.getItemCode(), quantityOnHand, lineItem.getQuantity().longValue());
                }
            }
            
            item.setItemName(lineItem.getItemName());
            item.setItemCode(normalizedItemCode);
            long quantityOnHand = item.getQuantityOnHand() == null ? 0L : item.getQuantityOnHand();
            item.setQuantityOnHand(quantityOnHand + direction * lineItem.getQuantity());
            inventoryItemRepository.save(item);

            lineItem.setInventoryItem(item);
            lineItem.setInventoryAdjusted(true);
        }

        log.info("Applied {} inventory adjustment for transaction {}", type, transaction.getTransactionId());
    }

    private InventoryItem createInventoryItem(POSTransactionLineItem lineItem) {
        InventoryItem item = new InventoryItem();
        item.setItemCode(lineItem.getItemCode());
        item.setItemName(lineItem.getItemName());
        item.setQuantityOnHand(0L);
        return inventoryItemRepository.save(item);
    }

    private InventoryItem mergeDuplicateItems(List<InventoryItem> duplicates, POSTransactionLineItem lineItem) {
        // Keep the first item as the primary and merge quantities from others
        InventoryItem primaryItem = duplicates.get(0);
        long totalQuantity = primaryItem.getQuantityOnHand() == null ? 0L : primaryItem.getQuantityOnHand();
        
        for (int i = 1; i < duplicates.size(); i++) {
            InventoryItem duplicate = duplicates.get(i);
            totalQuantity += duplicate.getQuantityOnHand() == null ? 0L : duplicate.getQuantityOnHand();
            inventoryItemRepository.delete(duplicate);
        }
        
        primaryItem.setQuantityOnHand(totalQuantity);
        primaryItem.setItemName(lineItem.getItemName());
        return inventoryItemRepository.save(primaryItem);
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> getItems() {
        return inventoryItemRepository.findAllByOrderByItemNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ItemReportDTO> getItemReport() {
        return itemReportRepository.findItemReport();
    }
}
