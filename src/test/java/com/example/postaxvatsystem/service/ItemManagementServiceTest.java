package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.model.InventoryItem;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.POSTransactionLineItem;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.dto.ItemReportDTO;
import com.example.postaxvatsystem.repository.InventoryItemRepository;
import com.example.postaxvatsystem.repository.ItemReportRepository;
import com.example.postaxvatsystem.repository.POSTransactionLineItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemManagementServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ItemReportRepository itemReportRepository;

    @Mock
    private POSTransactionLineItemRepository lineItemRepository;

    @InjectMocks
    private ItemManagementService itemManagementService;

    @Test
    void purchaseAddsLineItemQuantityToInventory() {
        InventoryItem item = existingItem(5L);
        POSTransactionLineItem lineItem = lineItem(3);
        POSTransaction transaction = transaction(TransactionType.PURCHASE, lineItem);
        when(inventoryItemRepository.findByItemCodeForUpdate("SKU-001")).thenReturn(List.of(item));

        itemManagementService.applySuccessfulTransaction(transaction);

        assertEquals(8L, item.getQuantityOnHand());
        assertEquals(item, lineItem.getInventoryItem());
        assertTrue(lineItem.getInventoryAdjusted());
        verify(inventoryItemRepository).save(item);
    }

    @Test
    void saleSubtractsLineItemQuantityFromInventory() {
        InventoryItem item = existingItem(5L);
        POSTransactionLineItem lineItem = lineItem(3);
        POSTransaction transaction = transaction(TransactionType.SALE, lineItem);
        when(inventoryItemRepository.findByItemCodeForUpdate("SKU-001")).thenReturn(List.of(item));

        itemManagementService.applySuccessfulTransaction(transaction);

        assertEquals(2L, item.getQuantityOnHand());
        assertTrue(lineItem.getInventoryAdjusted());
        verify(inventoryItemRepository).save(item);
    }

    @Test
    void treatsNullLegacyInventoryQuantityAsZeroDuringExpiryFinalization() {
        InventoryItem item = existingItem(0L);
        item.setQuantityOnHand(null);
        POSTransactionLineItem lineItem = lineItem(3);
        POSTransaction transaction = transaction(TransactionType.PURCHASE, lineItem);
        when(inventoryItemRepository.findByItemCodeForUpdate("SKU-001")).thenReturn(List.of(item));

        itemManagementService.applySuccessfulTransaction(transaction);

        assertEquals(3L, item.getQuantityOnHand());
        verify(inventoryItemRepository).save(item);
    }

    @Test
    void repeatedFinalizationDoesNotAdjustTheSameLineItemTwice() {
        POSTransactionLineItem lineItem = lineItem(3);
        lineItem.setInventoryAdjusted(true);
        POSTransaction transaction = transaction(TransactionType.SALE, lineItem);

        itemManagementService.applySuccessfulTransaction(transaction);

        verify(inventoryItemRepository, never()).findByItemCodeForUpdate("SKU-001");
        verify(inventoryItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void itemReportIsLoadedFromTheDedicatedReportRepository() {
        List<ItemReportDTO> report = List.of(new ItemReportDTO(
                "SKU-001", "Test item", 4L, 2L, 6L,
                new BigDecimal("200.00"), new BigDecimal("600.00"), 1L, 2L));
        when(itemReportRepository.findItemReport()).thenReturn(report);

        assertEquals(report, itemManagementService.getItemReport());
        verify(itemReportRepository).findItemReport();
    }

    private static InventoryItem existingItem(long quantity) {
        InventoryItem item = new InventoryItem();
        item.setItemCode("SKU-001");
        item.setItemName("Test item");
        item.setQuantityOnHand(quantity);
        return item;
    }

    private static POSTransactionLineItem lineItem(int quantity) {
        POSTransactionLineItem lineItem = new POSTransactionLineItem();
        lineItem.setItemCode("SKU-001");
        lineItem.setItemName("Test item");
        lineItem.setQuantity(quantity);
        lineItem.setInventoryAdjusted(false);
        return lineItem;
    }

    private static POSTransaction transaction(TransactionType type, POSTransactionLineItem lineItem) {
        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId("TXN-TEST-001");
        transaction.setTransactionType(type);
        transaction.setLineItems(List.of(lineItem));
        return transaction;
    }
}
