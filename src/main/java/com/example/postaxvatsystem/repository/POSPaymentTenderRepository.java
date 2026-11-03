package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.POSPaymentTender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface POSPaymentTenderRepository extends JpaRepository<POSPaymentTender, Long> {

    List<POSPaymentTender> findByTransactionId(Long transactionId);
}
