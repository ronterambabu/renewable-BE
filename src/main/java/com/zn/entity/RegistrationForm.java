package com.zn.entity;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RegistrationForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String phone;
    private String email;
    private String instituteOrUniversity;
    private String country;

    @ManyToOne
    @JoinColumn(name = "pricing_config_id")
    private PricingConfig pricingConfig;

    @Column(nullable = false)
    private BigDecimal amountPaid; // snapshot of totalPrice at registration time
    
    // One-to-One relationship with PaymentRecord
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_record_id", referencedColumnName = "id")
    @JsonManagedReference
    private com.zn.payment.entity.PaymentRecord paymentRecord;
}
