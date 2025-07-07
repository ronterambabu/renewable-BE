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
import com.zn.entity.PricingConfig;
import com.zn.entity.RegistrationForm;
import com.zn.payment.dto.CheckoutRequest;
import com.zn.payment.dto.PaymentResponseDTO;
import com.zn.payment.entity.PaymentRecord.PaymentStatus;
import com.zn.payment.service.StripeService;
import com.zn.repository.IPricingConfigRepository;
import com.zn.repository.IRegistrationFormRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/payment")
@Slf4j
public class PaymentController {

    @Autowired
    private StripeService stripeService;
    
    @Autowired
    private IRegistrationFormRepository registrationFormRepository;
    
    @Autowired
    private IPricingConfigRepository pricingConfigRepository;

    @PostMapping("/create-checkout-session")
    public ResponseEntity<PaymentResponseDTO> createCheckoutSession(@RequestBody CheckoutRequest request, @RequestParam Long pricingConfigId) {
        log.info("Received request to create checkout session: {} with mandatory pricingConfigId: {}", request, pricingConfigId);
        
        // Add detailed debugging for email field
        log.info("🔍 DEBUG - Request email field: '{}'", request.getEmail());
        log.info("🔍 DEBUG - Request name field: '{}'", request.getName());
        log.info("🔍 DEBUG - Request phone field: '{}'", request.getPhone());
        log.info("🔍 DEBUG - Full request object: {}", request);
        
        // Validate that pricingConfigId is provided (now mandatory)
        if (pricingConfigId == null) {
            log.error("pricingConfigId is mandatory but not provided");
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("pricing_config_id_required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
        
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
        
        // Validate required customer fields for registration
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            log.error("Customer email is required for registration. Request email: '{}', Request object: {}", 
                     request.getEmail(), request);
            log.error("❌ VALIDATION FAILED: Email field is missing or empty");
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("customer_email_required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            log.error("Customer name is required for registration");
            PaymentResponseDTO errorResponse = new PaymentResponseDTO();
            errorResponse.setStatus(PaymentStatus.FAILED);
            errorResponse.setPaymentStatus("customer_name_required");
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
        
        // Always use backend value for payment amount
        try {
            PricingConfig pricingConfig = pricingConfigRepository.findById(pricingConfigId)
                    .orElseThrow(() -> new IllegalArgumentException("Pricing config not found with ID: " + pricingConfigId));
            java.math.BigDecimal backendTotalPrice = pricingConfig.getTotalPrice();
            Long unitAmountInCents = backendTotalPrice.multiply(new java.math.BigDecimal(100)).longValue();
            request.setUnitAmount(unitAmountInCents); // Stripe expects cents
            log.info("Using backend total price for payment: {} EUR ({} cents)", backendTotalPrice, unitAmountInCents);
            
            // Set pricingConfigId in the request object (now mandatory)
            request.setPricingConfigId(pricingConfigId);
            log.info("Setting mandatory pricingConfigId: {}", pricingConfigId);
            
            // Create a complete registration form with all user data from checkout request
            RegistrationForm registrationForm = new RegistrationForm();
            registrationForm.setName(request.getName() != null ? request.getName() : "");
            registrationForm.setPhone(request.getPhone() != null ? request.getPhone() : "");
            registrationForm.setEmail(request.getEmail());
            registrationForm.setInstituteOrUniversity(request.getInstituteOrUniversity() != null ? request.getInstituteOrUniversity() : "");
            registrationForm.setCountry(request.getCountry() != null ? request.getCountry() : "");
            registrationForm.setPricingConfig(pricingConfig);
            registrationForm.setAmountPaid(pricingConfig.getTotalPrice());
            
            // Save the registration form first
            RegistrationForm savedRegistration = registrationFormRepository.save(registrationForm);
            log.info("✅ Registration form created and saved with ID: {}", savedRegistration.getId());
            
            // Create checkout session with pricing validation
            PaymentResponseDTO response = stripeService.createCheckoutSessionWithPricingValidation(request, pricingConfigId);
            log.info("Checkout session created successfully with pricing validation. Session ID: {}", response.getSessionId());
            
            // Now establish the association between RegistrationForm and PaymentRecord
            stripeService.linkRegistrationToPayment(savedRegistration.getId(), response.getSessionId());
            
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
        log.info("Webhook payload length: {}, Signature header present: {}", 
                payload.length(), sigHeader != null);
        
        if (sigHeader == null || sigHeader.isEmpty()) {
            log.error("⚠️ Missing Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature header");
        }
        
        try {
            Event event = stripeService.constructWebhookEvent(payload, sigHeader);
            log.info("✅ Webhook signature verified successfully. Event type: {}", event.getType());
            stripeService.processWebhookEvent(event);
            return ResponseEntity.ok().body("Webhook processed successfully");
        } catch (SignatureVerificationException e) {
            log.error("⚠️ Webhook signature verification failed: {}", e.getMessage());
            log.error("Payload length: {}, Signature header: {}", payload.length(), sigHeader);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature verification failed");
        } catch (Exception e) {
            log.error("❌ Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }
}
