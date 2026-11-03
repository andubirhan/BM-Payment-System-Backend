package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.migration.KeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Migration Controller for key rotation and data migration operations
 * These endpoints should only be used during migration and disabled in production
 */
@RestController
@RequestMapping("/api/v1/admin/migration")
@RequiredArgsConstructor
@Slf4j
public class MigrationController {

    private final KeyRotationService keyRotationService;

    /**
     * Trigger key rotation migration for payment tenders
     * This will decrypt data with old method and re-encrypt with PBKDF2
     * Requires ADMIN role
     */
    @PostMapping("/key-rotation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> migrateKeys() {
        log.info("Key rotation migration triggered by admin");
        try {
            KeyRotationService.MigrationSummary summary = keyRotationService.migrateAllPaymentTenders();
            String message = "Scanned %d tenders; migrated %d, skipped %d, errors %d."
                    .formatted(summary.scanned(), summary.migrated(), summary.skipped(), summary.errors());
            if (summary.errors() > 0) {
                return ResponseEntity.internalServerError().body(message);
            }
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Key rotation migration failed", e);
            return ResponseEntity.internalServerError()
                .body("Migration failed: " + e.getMessage());
        }
    }
}
