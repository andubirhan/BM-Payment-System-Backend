package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.POSAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface POSAuditLogRepository extends JpaRepository<POSAuditLog, Long> {

    List<POSAuditLog> findByTransactionId(Long transactionId);

    List<POSAuditLog> findByActorId(String actorId);
}
