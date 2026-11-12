package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.scheduler.PaymentReconciliationScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller for manual operations and testing.
 * All endpoints require ADMIN role for security.
 * 
 * This controller is only active when the PaymentReconciliationScheduler is available
 * (i.e., when the "fixture" profile is NOT active).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Profile("!fixture")
public class AdminController {

    private final PaymentReconciliationScheduler reconciliationScheduler;

    /**
     * Manually triggers the payment reconciliation scheduler.
     * This allows testing the reconciliation logic without waiting for the scheduled interval.
     * 
     * @return Response with reconciliation status
     */
    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerReconciliation() {
        log.info("Manual reconciliation triggered by admin");
        
        try {
            reconciliationScheduler.sweepStrandedPayments();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Reconciliation completed successfully");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during manual reconciliation", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Reconciliation failed");
            response.put("error", e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
