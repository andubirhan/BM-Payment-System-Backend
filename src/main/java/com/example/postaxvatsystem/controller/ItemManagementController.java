package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.model.InventoryItem;
import com.example.postaxvatsystem.dto.ItemReportDTO;
import com.example.postaxvatsystem.service.ItemManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pos/items")
@RequiredArgsConstructor
public class ItemManagementController {

    private final ItemManagementService itemManagementService;

    @GetMapping
    public List<InventoryItem> getItems() {
        return itemManagementService.getItems();
    }

    @GetMapping("/report")
    public List<ItemReportDTO> getItemReport() {
        return itemManagementService.getItemReport();
    }
}
