package com.zn.payment.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zn.payment.entity.PaymentRecord;
import com.zn.payment.entity.PaymentRecord.PaymentStatus;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    
    Optional<PaymentRecord> findBySessionId(String sessionId);
    
    Optional<PaymentRecord> findByPaymentIntentId(String paymentIntentId);
    
    List<PaymentRecord> findByStatus(PaymentStatus status);
    
    List<PaymentRecord> findByCustomerEmail(String customerEmail);
    
    long countByStatus(PaymentStatus status);
    
    List<PaymentRecord> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
    
    List<PaymentRecord> findByStatusOrderByCreatedAtDesc(PaymentStatus status);
    
    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.stripeExpiresAt < :now AND pr.status = 'PENDING'")
    List<PaymentRecord> findExpiredRecords(@Param("now") LocalDateTime now);
    
    @Query("SELECT SUM(pr.amountTotal) FROM PaymentRecord pr WHERE pr.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);
    
    // Pricing Config related queries
    List<PaymentRecord> findByPricingConfigId(Long pricingConfigId);
    
    List<PaymentRecord> findByPricingConfigIdAndStatus(Long pricingConfigId, PaymentStatus status);
    
    long countByPricingConfigIdAndStatus(Long pricingConfigId, PaymentStatus status);
    
    @Query("SELECT COUNT(pr) FROM PaymentRecord pr WHERE pr.pricingConfig.id = :pricingConfigId AND pr.status = 'COMPLETED'")
    long countSuccessfulPaymentsByPricingConfig(@Param("pricingConfigId") Long pricingConfigId);
    
    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.amountTotal != pr.pricingConfig.totalPrice")
    List<PaymentRecord> findPaymentsWithAmountMismatch();
}
