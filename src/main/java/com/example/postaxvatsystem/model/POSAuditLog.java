package com.example.postaxvatsystem.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "pos_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class POSAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private POSTransaction transaction;

    @Column(nullable = false, length = 36)
    private String actorId;

    @Column(nullable = false, length = 50)
    private String actorRole;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false)
    private TransactionStatus fromStatus;

    @Column(nullable = false)
    private TransactionStatus toStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payloadDetails;

    @Column(length = 255)
    private String ipAddress;

    @Column(length = 100)
    private String userAgent;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
