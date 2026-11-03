package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.TaxTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaxTransactionRepository extends JpaRepository<TaxTransaction, Long>,
        JpaSpecificationExecutor<TaxTransaction> {

    Optional<TaxTransaction> findByTaxTransactionId(String taxTransactionId);

    Optional<TaxTransaction> findByPosTransactionId(String posTransactionId);
}
