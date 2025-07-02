package com.zn.payment.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.payment.dto.PaymentResponseDTO;
import com.zn.payment.entity.PaymentRecord;
import com.zn.payment.entity.PaymentRecord.PaymentStatus;
import com.zn.payment.service.PaymentRecordService;
import com.zn.payment.service.PaymentRecordService.PaymentStatistics;

/**
 * REST Controller for payment record operations - ADMIN ACCESS ONLY
 */
@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('ADMIN')") // Require ADMIN role for all endpoints in this controller
public class PaymentRecordController {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentRecordController.class);

    @Autowired
    private PaymentRecordService paymentRecordService;

    /**
     * Utility method to get current authenticated admin user for logging
     */
    private String getCurrentAdminUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null ? authentication.getName() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Get payment record by session ID - ADMIN ONLY
     * Requires JWT authentication with ADMIN role
     */
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPaymentBySessionId(@PathVariable String sessionId) {
        String adminUser = getCurrentAdminUser();
        logger.info("ADMIN {}: Retrieving payment record for session: {}", adminUser, sessionId);
        
        try {
            Optional<PaymentRecord> payment = paymentRecordService.findBySessionId(sessionId);
            
            if (payment.isPresent()) {
                logger.info("ADMIN {}: Found payment record for session: {}", adminUser, sessionId);
                PaymentResponseDTO response = PaymentResponseDTO.fromEntity(payment.get());
                return ResponseEntity.ok(response);
            } else {
                logger.warn("ADMIN {}: Payment record not found for session: {}", adminUser, sessionId);
                return ResponseEntity.ok().body(Map.of(
                    "message", "Payment record not found",
                    "sessionId", sessionId,
                    "found", false
                ));
            }
        } catch (Exception e) {
            logger.error("ADMIN {}: Error retrieving payment for session {}: {}", adminUser, sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Error retrieving payment record",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get payment records by customer email - ADMIN ONLY
     * Requires JWT authentication with ADMIN role
     */
    @GetMapping("/customer/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentsByCustomer(@PathVariable String email) {
        String adminUser = getCurrentAdminUser();
        logger.info("ADMIN {}: Retrieving payment records for customer: {}", adminUser, email);
        
        try {
            List<PaymentRecord> payments = paymentRecordService.findByCustomerEmail(email);
            List<PaymentResponseDTO> response = payments.stream()
                .map(PaymentResponseDTO::fromEntity)
                .collect(java.util.stream.Collectors.toList());
            logger.info("ADMIN {}: Found {} payment records for customer: {}", adminUser, payments.size(), email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("ADMIN {}: Error retrieving payments for customer {}: {}", adminUser, email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get payment records by status - ADMIN ONLY
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentsByStatus(@PathVariable PaymentStatus status) {
        String adminUser = getCurrentAdminUser();
        logger.info("ADMIN {}: Retrieving payment records with status: {}", adminUser, status);
        
        try {
            List<PaymentRecord> payments = paymentRecordService.findByStatus(status);
            List<PaymentResponseDTO> response = payments.stream()
                .map(PaymentResponseDTO::fromEntity)
                .collect(java.util.stream.Collectors.toList());
            logger.info("ADMIN {}: Found {} payment records with status: {}", adminUser, payments.size(), status);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("ADMIN {}: Error retrieving payments by status {}: {}", adminUser, status, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all expired payment records - ADMIN ONLY
     */
    @GetMapping("/expired")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponseDTO>> getExpiredPayments() {
        String adminUser = getCurrentAdminUser();
        logger.info("ADMIN {}: Retrieving expired payment records", adminUser);
        
        try {
            List<PaymentRecord> expiredPayments = paymentRecordService.findExpiredRecords();
            List<PaymentResponseDTO> response = expiredPayments.stream()
                .map(PaymentResponseDTO::fromEntity)
                .collect(java.util.stream.Collectors.toList());
            logger.info("ADMIN {}: Found {} expired payment records", adminUser, expiredPayments.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("ADMIN {}: Error retrieving expired payments: {}", adminUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Mark expired records as EXPIRED - ADMIN ONLY
     */
    @GetMapping("/expire-stale")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> markExpiredRecords() {
        logger.info("ADMIN: Marking expired payment records");
        
        try {
            int count = paymentRecordService.markExpiredRecords();
            String message = "ADMIN: Marked " + count + " payment records as expired";
            logger.info(message);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            logger.error("ADMIN: Error marking expired records: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("ADMIN: Error marking expired records: " + e.getMessage());
        }
    }

    /**
     * Get payment statistics - ADMIN ONLY
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentStatistics> getPaymentStatistics() {
        logger.info("ADMIN: Retrieving payment statistics");
        
        try {
            PaymentStatistics stats = paymentRecordService.getPaymentStatistics();
            logger.info("ADMIN: Retrieved payment statistics successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("ADMIN: Error retrieving payment statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint for admin - ADMIN ONLY
     */
    @GetMapping("/admin/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> adminHealthCheck() {
        logger.info("ADMIN: Health check requested");
        return ResponseEntity.ok("ADMIN: Payment Records API is healthy and secured");
    }

    /**
     * Get payment statistics - ADMIN ONLY
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentStatistics> getPaymentStats() {
        String adminUser = getCurrentAdminUser();
        logger.info("ADMIN {}: Retrieving payment statistics", adminUser);
        
        try {
            PaymentStatistics stats = paymentRecordService.getPaymentStatistics();
            logger.info("ADMIN: Retrieved enhanced payment statistics successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("ADMIN: Error retrieving payment statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Test admin authentication - ADMIN ONLY  
     * Use this endpoint to verify JWT authentication is working
     */
    @GetMapping("/admin/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> testAdminAuth() {
        String adminUser = getCurrentAdminUser();
        logger.info("ADMIN {}: Authentication test successful", adminUser);
        
        return ResponseEntity.ok(Map.of(
            "message", "Admin authentication successful",
            "adminUser", adminUser,
            "timestamp", java.time.LocalDateTime.now(),
            "status", "authenticated"
        ));
    }
}
