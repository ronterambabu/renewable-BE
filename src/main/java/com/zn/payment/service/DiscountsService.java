package com.zn.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.zn.payment.dto.CreateDiscountSessionRequest;

@Service
public class DiscountsService {
      @Value("${stripe.api.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    public String createSession(CreateDiscountSessionRequest request) {
        // Implementation for creating a discount session
        if (request.getUnitAmount() == null || request.getUnitAmount() <= 0) {
            return "Unit amount must be positive";
        }

        // Here you would typically call Stripe's API to create a session
        // implement the logic to create a Stripe session using the request data
        
        
    
        return "Session created successfully";
    }
}
