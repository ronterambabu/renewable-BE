package com.zn.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PricingConfigResponseDTO {
	 private BigDecimal registrationFee;
	    private BigDecimal accommodationPrice;
	    private BigDecimal processingFeePercent;
	    private BigDecimal totalPrice;
    // Getters and setters
}
