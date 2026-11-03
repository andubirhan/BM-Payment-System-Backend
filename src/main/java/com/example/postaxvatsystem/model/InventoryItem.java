package com.example.postaxvatsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pos_items", uniqueConstraints = @UniqueConstraint(name = "uk_pos_items_item_code", columnNames = "item_code"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_code", nullable = false, length = 100)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "quantity_on_hand", nullable = false)
    private Long quantityOnHand = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
