package com.zn.dto;

import java.math.BigDecimal;

import com.zn.entity.Accommodation;
import com.zn.entity.PresentationType;

import lombok.Data;

@Data
public class PricingConfigResponseDTO {
	  private Long id;
	    private BigDecimal totalPrice;
	    private double processingFeePercent;
	    private PresentationType presentationType;
	    private Accommodation accommodationOption;
}
