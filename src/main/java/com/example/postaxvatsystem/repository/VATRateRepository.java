package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.VATRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VATRateRepository extends JpaRepository<VATRate, Long> {

    Optional<VATRate> findByCategoryCode(String categoryCode);

    List<VATRate> findByActiveTrue();
}
