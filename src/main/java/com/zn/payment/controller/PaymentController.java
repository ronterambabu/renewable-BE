package com.zn.payment.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.zn.payment.dto.CheckoutRequest;
import com.zn.payment.dto.PaymentResponseDTO;
import com.zn.payment.entity.PaymentRecord.PaymentStatus;
import com.zn.payment.service.StripeService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/payment")
@Slf4j
public class PaymentController {

    @Autowired
    private StripeService stripeService;

    @PostMapping("/create-checkout-session")
    public ResponseEntity<PaymentResponseDTO> createCheckoutSession(@RequestBody CheckoutRequest request, @RequestParam(required = false) Long pricingConfigId) {
        log.info("Received request to create checkout session: {}", request);
        
        // Validate incoming request currency is EUR only
        if (request.getCurrency() == null) {
            request.setCurrency("eur"); // Default to EUR if not provided
            log.info("Currency not provided, defaulting to EUR");
        } else if (!"eur".equalsIgnoreCase(request.getCurrency())) {
            log.error("Invalid currency provided: {}. Only EUR is supported", request.getCurrency());
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("invalid_currency_only_eur_supported");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
        
        // Validate required fields for EUR payment (amounts come in euros, not cents)
        if (request.getUnitAmount() == null || request.getUnitAmount() <= 0) {
            log.error("Invalid unitAmount: {}. Must be positive value in euros", request.getUnitAmount());
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("invalid_amount_must_be_positive_euros");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
        
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            log.error("Invalid quantity: {}. Must be positive value", request.getQuantity());
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("invalid_quantity_must_be_positive");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
        
        // Convert euro amounts to cents for Stripe API (Stripe expects cents)
        Long unitAmountInCents = (long) (request.getUnitAmount() * 100);
        request.setUnitAmount(unitAmountInCents); // Convert euros to cents
        
        log.info("✅ Request validation passed: {} EUR converted to {} cents, quantity: {}", 
                request.getUnitAmount() / 100.0, request.getUnitAmount(), request.getQuantity());
         
        // If pricingConfigId is provided as request parameter, set it in the request object
        if (pricingConfigId != null) {
            request.setPricingConfigId(pricingConfigId);
            log.info("Setting pricingConfigId from request param: {}", pricingConfigId);
        }
        
        try {
            PaymentResponseDTO response;
            
            // If pricingConfigId is provided, use pricing validation method
            if (pricingConfigId != null) {
                // Call service method that fetches pricing config and validates amount
                response = stripeService.createCheckoutSessionWithPricingValidation(request, pricingConfigId);
            } else {
                // For requests without pricing config, use standard EUR validation only
                response = stripeService.createCheckoutSessionWithoutPricingValidation(request);
            }
            
            log.info("Checkout session created successfully. Session ID: {}", response.getSessionId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating checkout session: {}", e.getMessage());
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("validation_failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        } catch (Exception e) {
            log.error("Error creating checkout session: {}", e.getMessage(), e);
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDTO> getCheckoutSession(@PathVariable String id) {
        log.info("Retrieving checkout session with ID: {}", id);
        try {
            PaymentResponseDTO responseDTO = stripeService.retrieveSession(id);
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            log.error("Error retrieving checkout session: {}", e.getMessage(), e);
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }
    
    @PostMapping("/{id}/expire")
    public ResponseEntity<PaymentResponseDTO> expireSession(@PathVariable String id) {
        log.info("Expiring checkout session with ID: {}", id);
        try {
            PaymentResponseDTO responseDTO = stripeService.expireSession(id);
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            log.error("Error expiring checkout session: {}", e.getMessage(), e);
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) throws IOException {
        log.info("Received webhook request");
        String payload;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()))) {
            payload = reader.lines().collect(Collectors.joining("\n"));
        }
        
        String sigHeader = request.getHeader("Stripe-Signature");
        
        try {
            Event event = stripeService.constructWebhookEvent(payload, sigHeader);
            stripeService.processWebhookEvent(event);
            return ResponseEntity.ok().body("Webhook processed successfully");
        } catch (SignatureVerificationException e) {
            log.error("⚠️ Webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature verification failed");
        }
    }
}
