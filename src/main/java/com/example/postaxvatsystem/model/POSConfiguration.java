package com.example.postaxvatsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pos_configuration")
@Data
@NoArgsConstructor
public class POSConfiguration {

    @Id
    @Column(name = "config_key", length = 100)
    private String key;

    @Column(name = "config_value", nullable = false, length = 255)
    private String value;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
