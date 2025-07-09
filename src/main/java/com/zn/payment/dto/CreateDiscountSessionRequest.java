package com.zn.payment.dto;

import lombok.Data;

@Data
public class CreateDiscountSessionRequest {
    private String productName;
    private String description;
    private String orderReference;
    private Long unitAmount; // in cents (EURO only - e.g., 4500 for €45.00)
    private Long quantity;
    private String currency; // Optional: defaults to "eur" if not provided. Only "eur" is supported.
    private String successUrl;
    private String cancelUrl;
}
