package com.zn.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
@Entity
@Data
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
    
    // Reference to the PaymentRecord that triggered this registration
    @Column(name = "payment_record_id")
    private Long paymentRecordId;
}
