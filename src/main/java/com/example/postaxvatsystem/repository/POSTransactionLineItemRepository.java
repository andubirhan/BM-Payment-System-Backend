package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.POSTransactionLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface POSTransactionLineItemRepository extends JpaRepository<POSTransactionLineItem, Long> {

    List<POSTransactionLineItem> findByTransactionId(Long transactionId);
}
