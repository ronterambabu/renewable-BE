package com.zn.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Data
public class PricingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "presentation_type_id", nullable = false)
    private PresentationType presentationType;

    @ManyToOne
    @JoinColumn(name = "accommodation_option_id")
    private Accommodation accommodationOption;

    @Column(nullable = false)
    private double processingFeePercent;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    @PrePersist
    @PreUpdate
    public void calculateTotalPrice() {
        if (presentationType == null || presentationType.getPrice() == null) {
            throw new IllegalStateException("Presentation type or its price must not be null.");
        }

        BigDecimal presPrice = presentationType.getPrice();
        BigDecimal accPrice = (accommodationOption != null && accommodationOption.getPrice() != null)
                ? accommodationOption.getPrice()
                : BigDecimal.ZERO;

        BigDecimal subtotal = presPrice.add(accPrice);
        BigDecimal fee = subtotal.multiply(BigDecimal.valueOf(processingFeePercent / 100.0));
        this.totalPrice = subtotal.add(fee).setScale(2, RoundingMode.HALF_UP);
    }
}
