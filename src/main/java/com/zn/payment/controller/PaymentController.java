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
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
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
    public ResponseEntity<PaymentResponseDTO> createCheckoutSession(@RequestBody CheckoutRequest request) {
        log.info("Received request to create checkout session: {}", request);
        
        // Check if pricingConfigId is provided for validation
        if (request.getPricingConfigId() != null) {
            // Use validated checkout for requests with pricing config
            return createValidatedCheckoutSession(request);
        }
        
        // Fallback to legacy method for backwards compatibility
        try {
            // Get complete session details from service
            Session session = stripeService.createDetailedCheckoutSession(request);
            
            // Use service method to map session to DTO with proper timestamp conversion
            PaymentResponseDTO response = stripeService.mapSessionToResponceDTO(session);
            
            log.info("Checkout session created successfully. Session ID: {}", session.getId());
            return ResponseEntity.ok(response);
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

    @PostMapping("/create-validated-checkout-session")
    public ResponseEntity<PaymentResponseDTO> createValidatedCheckoutSession(@RequestBody CheckoutRequest request) {
        log.info("Received request to create validated checkout session: {}", request);
        try {
            // Use validated checkout session creation with pricing config validation
            PaymentResponseDTO response = stripeService.createValidatedCheckoutSession(request);
            
            log.info("Validated checkout session created successfully. Session ID: {}", response.getSessionId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating checkout session: {}", e.getMessage());
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("validation_failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        } catch (Exception e) {
            log.error("Error creating validated checkout session: {}", e.getMessage(), e);
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }
}
