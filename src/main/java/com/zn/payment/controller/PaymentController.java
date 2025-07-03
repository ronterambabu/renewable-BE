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
import com.zn.controller.RegistrationController;
import com.zn.entity.RegistrationForm;
import com.zn.entity.PricingConfig;
import com.zn.payment.dto.CheckoutRequest;
import com.zn.payment.dto.PaymentResponseDTO;
import com.zn.payment.entity.PaymentRecord.PaymentStatus;
import com.zn.payment.service.StripeService;
import com.zn.repository.IRegistrationFormRepository;
import com.zn.repository.IPricingConfigRepository;

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
    
    @Autowired
    private RegistrationController registrationController;

    @PostMapping("/create-checkout-session")
    public ResponseEntity<PaymentResponseDTO> createCheckoutSession(@RequestBody CheckoutRequest request, @RequestParam Long pricingConfigId) {
        log.info("Received request to create checkout session: {} with mandatory pricingConfigId: {}", request, pricingConfigId);
        
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
            log.error("Customer email is required for registration");
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
         
        // Set pricingConfigId in the request object (now mandatory)
        request.setPricingConfigId(pricingConfigId);
        log.info("Setting mandatory pricingConfigId: {}", pricingConfigId);
        
        try {
            // Always use pricing validation method since pricingConfigId is now mandatory
            PaymentResponseDTO response = stripeService.createCheckoutSessionWithPricingValidation(request, pricingConfigId);
            
            log.info("Checkout session created successfully with pricing validation. Session ID: {}", response.getSessionId());
            
            // Get the pricing config to link to the registration form
            PricingConfig pricingConfig = pricingConfigRepository.findById(pricingConfigId)
                    .orElseThrow(() -> new IllegalArgumentException("Pricing config not found with ID: " + pricingConfigId));
            
            // Create a complete registration form with all user data from checkout request
            RegistrationForm registrationForm = new RegistrationForm();
            registrationForm.setName(request.getName() != null ? request.getName() : "");
            registrationForm.setPhone(request.getPhone() != null ? request.getPhone() : "");
            registrationForm.setEmail(request.getEmail());
            registrationForm.setInstituteOrUniversity(request.getInstituteOrUniversity() != null ? request.getInstituteOrUniversity() : "");
            registrationForm.setCountry(request.getCountry() != null ? request.getCountry() : "");
            registrationForm.setPricingConfig(pricingConfig);
            registrationForm.setAmountPaid(pricingConfig.getTotalPrice()); // Set the amount paid from pricing config
            
            // Call the registration controller's registerUser method to properly validate and save
            try {
                ResponseEntity<?> registrationResponse = registrationController.registerUser(registrationForm);
                
                if (registrationResponse.getStatusCode().is2xxSuccessful()) {
                    Object responseBody = registrationResponse.getBody();
                    if (responseBody instanceof RegistrationForm) {
                        RegistrationForm savedRegistration = (RegistrationForm) responseBody;
                        log.info("✅ Registration form created and saved with ID: {} for session: {}", 
                                savedRegistration.getId(), response.getSessionId());
                    } else {
                        log.info("✅ Registration completed successfully for session: {}", response.getSessionId());
                    }
                } else {
                    log.error("❌ Registration failed: {}", registrationResponse.getBody());
                    // Continue with payment session creation even if registration fails
                }
            } catch (Exception registrationException) {
                log.error("❌ Error during registration: {}", registrationException.getMessage());
                // Continue with payment session creation even if registration fails
            }
            
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
