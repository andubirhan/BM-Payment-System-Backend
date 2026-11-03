package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.BankPaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for bank payment audit logs.
 * Per BRD SEC-3.1: This table should have append-only access privileges.
 */
@Repository
public interface BankPaymentAuditLogRepository extends JpaRepository<BankPaymentAuditLog, Long> {
}
