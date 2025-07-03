package com.zn.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.zn.entity.PricingConfig;
import com.zn.payment.dto.CheckoutRequest;
import com.zn.payment.dto.PaymentResponseDTO;
import com.zn.payment.entity.PaymentRecord;
import com.zn.payment.repository.PaymentRecordRepository;
import com.zn.repository.IPricingConfigRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Stripe Payment Service - EURO ONLY PAYMENTS
 * 
 * This service handles all Stripe payment operations and enforces EURO-only payments throughout the system.
 * 
 * Key Features:
 * - All payments are processed in EUR currency only
 * - Amounts are stored in euros (BigDecimal) in the database
 * - Stripe checkout sessions are created with EUR currency
 * - Payment dashboard reports show amounts in euros
 * - Pricing config validation ensures amounts match exactly in euros
 * - All Stripe API calls use EUR currency
 * 
 * Currency Policy:
 * - Only "eur" currency is accepted in all checkout requests
 * - Unit amounts in requests come in euros (e.g., 45.00) and are converted to cents by the controller
 * - Stripe API receives amounts in cents (e.g., 4500 = €45.00)
 * - Database stores amounts in euros (e.g., 45.00)
 * - Stripe dashboard will display all payments in euros
 * - Payment reports and statistics show euro values
 * 
 * @author System
 */
@Service
@Slf4j
public class StripeService {
    private static final ZoneId US_ZONE = ZoneId.of("America/New_York");

    @Value("${stripe.api.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;
    
    @Autowired
    private PaymentRecordRepository paymentRecordRepository;
    
    @Autowired
    private IPricingConfigRepository pricingConfigRepository;
    
    private LocalDateTime convertToLocalDateTime(Long timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp)
                     .atZone(US_ZONE)
                     .toLocalDateTime();
    }

    public PaymentResponseDTO mapSessionToResponceDTO(Session session) {
        PaymentResponseDTO responseDTO = new PaymentResponseDTO();
        responseDTO.setSessionId(session.getId());
        responseDTO.setUrl(session.getUrl()); // Add the checkout URL
        responseDTO.setPaymentStatus(session.getPaymentStatus());
        
        // Convert timestamps to LocalDateTime
        LocalDateTime createdTime = convertToLocalDateTime(session.getCreated());
        LocalDateTime expiresTime = convertToLocalDateTime(session.getExpiresAt());
        
        responseDTO.setStripeCreatedAt(createdTime);
        responseDTO.setStripeExpiresAt(expiresTime);
        
        // Map other fields from session metadata if available
        if (session.getMetadata() != null) {
            responseDTO.setCustomerName(session.getMetadata().get("customerName"));
            responseDTO.setProductName(session.getMetadata().get("productName"));
        }
        
        return responseDTO;
    }

    /**
     * Create complete response DTO with both Stripe session and database record information
     */
    public PaymentResponseDTO createCompleteResponseDTO(Session session, PaymentRecord paymentRecord) {
        PaymentResponseDTO responseDTO = new PaymentResponseDTO();
        
        // Map Stripe session information
        responseDTO.setSessionId(session.getId());
        responseDTO.setUrl(session.getUrl());
        responseDTO.setPaymentStatus(session.getPaymentStatus());
        responseDTO.setStripeCreatedAt(convertToLocalDateTime(session.getCreated()));
        responseDTO.setStripeExpiresAt(convertToLocalDateTime(session.getExpiresAt()));
        
        // Map database record information
        responseDTO.setId(paymentRecord.getId());
        responseDTO.setCustomerEmail(paymentRecord.getCustomerEmail());
        responseDTO.setAmountTotalEuros(paymentRecord.getAmountTotal());
        responseDTO.setAmountTotalCents(paymentRecord.getAmountTotal().multiply(BigDecimal.valueOf(100)).longValue());
        responseDTO.setCurrency(paymentRecord.getCurrency());
        responseDTO.setStatus(paymentRecord.getStatus());
        responseDTO.setCreatedAt(paymentRecord.getCreatedAt());
        responseDTO.setUpdatedAt(paymentRecord.getUpdatedAt());
        
        // Map pricing config information if available
        if (paymentRecord.getPricingConfig() != null) {
            responseDTO.setPricingConfigId(paymentRecord.getPricingConfig().getId());
            responseDTO.setPricingConfigTotalPrice(paymentRecord.getPricingConfig().getTotalPrice());
        }
        
        // Map other fields from session metadata if available
        if (session.getMetadata() != null) {
            responseDTO.setCustomerName(session.getMetadata().get("customerName"));
            responseDTO.setProductName(session.getMetadata().get("productName"));
        }
        
        log.info("✅ Created complete response DTO with DB ID: {} and session ID: {}", 
                paymentRecord.getId(), session.getId());
        
        return responseDTO;
    }

    public Session createDetailedCheckoutSession(CheckoutRequest request) throws StripeException {
        log.info("Creating detailed checkout session for product: {}", request.getProductName());
        
        // Enforce EURO-only payments
        validateEuroCurrency(request);
        
        Stripe.apiKey = secretKey;

        // Create metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("productName", request.getProductName());
        if (request.getOrderReference() != null) {
            metadata.put("orderReference", request.getOrderReference());
        }

        // Set expiration time (US Eastern)
        ZonedDateTime expirationTime = ZonedDateTime.now(US_ZONE).plus(30, ChronoUnit.MINUTES);

        // Build Stripe SessionCreateParams - enforcing EUR currency
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(request.getSuccessUrl())
            .setCancelUrl(request.getCancelUrl())
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .setExpiresAt(expirationTime.toEpochSecond())
            .putAllMetadata(metadata)
            .setCustomerEmail(request.getCustomerEmail())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(request.getQuantity())
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("eur") // Always use EUR
                            .setUnitAmount(request.getUnitAmount())
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(request.getProductName())
                                    .setDescription(request.getDescription())
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build();

    try {
        Session session = Session.create(params);

        log.info("✅ Created checkout session with ID: {} at {}", 
                session.getId(), 
                convertToLocalDateTime(session.getCreated()));

        // 💾 Save to DB with status PENDING - always EUR currency
        PaymentRecord record = PaymentRecord.builder()
            .sessionId(session.getId())
            .paymentIntentId(null)
            .customerEmail(request.getCustomerEmail())
            .amountTotal(BigDecimal.valueOf(request.getUnitAmount() * request.getQuantity()).divide(BigDecimal.valueOf(100))) // Convert cents to euros
            .currency("eur") // Always EUR
            .status(PaymentRecord.PaymentStatus.PENDING) // Initial status should be PENDING
            .stripeCreatedAt(convertToLocalDateTime(session.getCreated()))
            .stripeExpiresAt(convertToLocalDateTime(session.getExpiresAt()))
            .paymentStatus(session.getPaymentStatus())
            .build();

        paymentRecordRepository.save(record);
        log.info("💾 Saved PaymentRecord for session: {}", session.getId());

        return session;

    } catch (StripeException e) {
        log.error("❌ Error creating checkout session: {}", e.getMessage());
        throw e;
    }
}

    /**
     * Create checkout session without pricing config validation
     * Only validates EUR currency and creates session directly
     */
    public PaymentResponseDTO createCheckoutSessionWithoutPricingValidation(CheckoutRequest request) throws StripeException {
        log.info("Creating checkout session without pricing validation for product: {}", request.getProductName());
        
        // Validate EUR currency
        validateEuroCurrency(request);
        
        // Create Stripe session using existing method
        Session session = createDetailedCheckoutSession(request);
        
        // Fetch the saved PaymentRecord from database
        PaymentRecord paymentRecord = paymentRecordRepository.findBySessionId(session.getId())
            .orElseThrow(() -> new RuntimeException("PaymentRecord not found after creation for session: " + session.getId()));
        
        // Create complete response DTO with both Stripe and DB information
        PaymentResponseDTO response = createCompleteResponseDTO(session, paymentRecord);
        log.info("✅ Checkout session created without pricing validation: {} with DB ID: {}", 
                session.getId(), paymentRecord.getId());
        
        return response;
    }

    /**
     * Create checkout session with pricing config validation
     * This ensures the payment amount matches the configured pricing
     */
    public Session createCheckoutSessionWithPricing(CheckoutRequest request, PricingConfig pricingConfig) throws StripeException {
        log.info("Creating checkout session with pricing config validation for product: {}", request.getProductName());
        
        // Validate EUR currency first
        validateEuroCurrency(request);
        
        // Validate amount matches pricing config
        Long expectedAmountInCents = pricingConfig.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();
        Long requestedAmount = request.getUnitAmount() * request.getQuantity();
        
        if (!expectedAmountInCents.equals(requestedAmount)) {
            throw new IllegalArgumentException(
                String.format("Payment amount (%d cents) does not match pricing config total (%d cents)", 
                            requestedAmount, expectedAmountInCents));
        }
        
        Stripe.apiKey = secretKey;

        // Create metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("productName", request.getProductName());
        metadata.put("pricingConfigId", pricingConfig.getId().toString());
        if (request.getOrderReference() != null) {
            metadata.put("orderReference", request.getOrderReference());
        }

        // Set expiration time (US Eastern)
        ZonedDateTime expirationTime = ZonedDateTime.now(US_ZONE).plus(30, ChronoUnit.MINUTES);

        // Build Stripe SessionCreateParams - enforcing EUR currency
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(request.getSuccessUrl())
            .setCancelUrl(request.getCancelUrl())
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .setExpiresAt(expirationTime.toEpochSecond())
            .putAllMetadata(metadata)
            .setCustomerEmail(request.getCustomerEmail())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(request.getQuantity())
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("eur") // Always use EUR
                            .setUnitAmount(request.getUnitAmount())
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(request.getProductName())
                                    .setDescription(request.getDescription())
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build();

        try {
            Session session = Session.create(params);

            log.info("✅ Created checkout session with ID: {} at {} for pricing config: {}", 
                    session.getId(), 
                    convertToLocalDateTime(session.getCreated()),
                    pricingConfig.getId());

            // 💾 Save to DB with pricing config relationship - always EUR currency
            PaymentRecord record = PaymentRecord.fromStripeWithPricing(
                session.getId(),
                request.getCustomerEmail(),
                "eur", // Always EUR
                pricingConfig,
                convertToLocalDateTime(session.getCreated()),
                convertToLocalDateTime(session.getExpiresAt()),
                session.getPaymentStatus()
            );

            paymentRecordRepository.save(record);
            log.info("💾 Saved PaymentRecord for session: {} with pricing config: {}", 
                    session.getId(), pricingConfig.getId());

            return session;

        } catch (StripeException e) {
            log.error("❌ Error creating checkout session with pricing: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Create checkout session with strict validation against pricing config
     * Validates amount, currency, and ensures all values are in euros
     */
    public PaymentResponseDTO createValidatedCheckoutSession(CheckoutRequest request) throws StripeException {
        log.info("Creating validated checkout session for pricingConfigId: {}", request.getPricingConfigId());
        
        // 1. Validate required fields
        if (request.getPricingConfigId() == null) {
            throw new IllegalArgumentException("Pricing config ID is required for checkout validation");
        }
        
        // 2. Validate currency is EUR
        validateEuroCurrency(request);
        
        // 3. Fetch pricing config
        PricingConfig pricingConfig = pricingConfigRepository.findById(request.getPricingConfigId())
            .orElseThrow(() -> new IllegalArgumentException("Pricing config not found with ID: " + request.getPricingConfigId()));
        
        log.info("Found pricing config with total price: {} EUR", pricingConfig.getTotalPrice());
        
        // 4. Validate unitAmount matches pricing config (convert euros to cents for comparison)
        Long expectedAmountInCents = pricingConfig.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();
        Long requestedTotalInCents = request.getUnitAmount() * request.getQuantity();
        
        if (!expectedAmountInCents.equals(requestedTotalInCents)) {
            log.error("Payment amount validation failed. Expected: {} cents ({} EUR), Requested: {} cents ({} EUR)", 
                     expectedAmountInCents, pricingConfig.getTotalPrice(), 
                     requestedTotalInCents, BigDecimal.valueOf(requestedTotalInCents).divide(BigDecimal.valueOf(100)));
            throw new IllegalArgumentException(
                String.format("Payment amount mismatch. Expected: %s EUR, but received: %s EUR", 
                            pricingConfig.getTotalPrice(),
                            BigDecimal.valueOf(requestedTotalInCents).divide(BigDecimal.valueOf(100))));
        }
        
        log.info("✅ Amount validation passed: {} EUR", pricingConfig.getTotalPrice());
        
        // 5. Create Stripe session with validated amounts
        Session session = createCheckoutSessionWithPricing(request, pricingConfig);
        
        // 6. Fetch the saved PaymentRecord from database
        PaymentRecord paymentRecord = paymentRecordRepository.findBySessionId(session.getId())
            .orElseThrow(() -> new RuntimeException("PaymentRecord not found after creation for session: " + session.getId()));
        
        // 7. Create complete response DTO with both Stripe and DB information
        PaymentResponseDTO response = createCompleteResponseDTO(session, paymentRecord);
        log.info("✅ Validated checkout session created: {} with DB ID: {}", session.getId(), paymentRecord.getId());
        
        return response;
    }

    /**
     * Create checkout session with pricing config ID validation
     * Fetches pricing config by ID and validates amount matches exactly
     */
    public PaymentResponseDTO createCheckoutSessionWithPricingValidation(CheckoutRequest request, Long pricingConfigId) throws StripeException {
        log.info("Creating checkout session with pricing config ID: {}", pricingConfigId);
        
        // 1. Validate currency is EUR
        validateEuroCurrency(request);
        
        // 2. Fetch pricing config by ID
        PricingConfig pricingConfig = pricingConfigRepository.findById(pricingConfigId)
            .orElseThrow(() -> new IllegalArgumentException("Pricing config not found with ID: " + pricingConfigId));
        
        log.info("Found pricing config with total price: {} EUR", pricingConfig.getTotalPrice());
        
        // 3. Validate unitAmount matches pricing config
        // Note: request.getUnitAmount() is already in cents (converted by controller)
        // pricingConfig.getTotalPrice() is in euros, so convert to cents for comparison
        Long expectedTotalInCents = pricingConfig.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();
        Long requestedTotalInCents = request.getUnitAmount() * request.getQuantity();
        
        if (!expectedTotalInCents.equals(requestedTotalInCents)) {
            BigDecimal expectedEuros = pricingConfig.getTotalPrice();
            BigDecimal requestedEuros = BigDecimal.valueOf(requestedTotalInCents).divide(BigDecimal.valueOf(100));
            
            log.error("Payment amount validation failed. Expected: {} cents ({} EUR), Requested: {} cents ({} EUR)", 
                     expectedTotalInCents, expectedEuros, 
                     requestedTotalInCents, requestedEuros);
            throw new IllegalArgumentException(
                String.format("Payment amount mismatch. Expected: %s EUR, but received: %s EUR", 
                            expectedEuros, requestedEuros));
        }
        
        log.info("✅ Amount validation passed: {} EUR", pricingConfig.getTotalPrice());
        
        // 4. Create Stripe session using the existing method
        Session session = createCheckoutSessionWithPricing(request, pricingConfig);
        
        // 5. Fetch the saved PaymentRecord from database
        PaymentRecord paymentRecord = paymentRecordRepository.findBySessionId(session.getId())
            .orElseThrow(() -> new RuntimeException("PaymentRecord not found after creation for session: " + session.getId()));
        
        // 6. Create complete response DTO with both Stripe and DB information
        PaymentResponseDTO response = createCompleteResponseDTO(session, paymentRecord);
        log.info("✅ Checkout session created with pricing validation: {} with DB ID: {}", 
                session.getId(), paymentRecord.getId());
        
        return response;
    }

    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        log.debug("Constructing webhook event from payload with signature");
        return Webhook.constructEvent(payload, sigHeader, endpointSecret);
    }

    public void processWebhookEvent(Event event) {
        log.info("Processing webhook event of type: {}", event.getType());
        
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            // Add more event type handlers here as needed
            default -> log.info("Unhandled event type: {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isPresent()) {
            Session session = (Session) dataObjectDeserializer.getObject().get();
            LocalDateTime completedTime = convertToLocalDateTime(session.getCreated());

            log.info("✅ Payment successful for session: {} at {}", session.getId(), completedTime);
            log.info("💳 Customer email: {}", session.getCustomerDetails() != null ?
                    session.getCustomerDetails().getEmail() : "N/A");
            log.info("💰 Amount total: {}", session.getAmountTotal());

            // Update existing record instead of creating new one
            try {
                PaymentRecord existingRecord = paymentRecordRepository.findBySessionId(session.getId())
                    .orElse(null);
                
                if (existingRecord != null) {
                    // Update existing record
                    existingRecord.setPaymentIntentId(session.getPaymentIntent());
                    existingRecord.setStatus(PaymentRecord.PaymentStatus.COMPLETED);
                    // Update customer email if it was null before
                    if (existingRecord.getCustomerEmail() == null && session.getCustomerDetails() != null) {
                        existingRecord.setCustomerEmail(session.getCustomerDetails().getEmail());
                    }
                    
                    paymentRecordRepository.save(existingRecord);
                    log.info("💾 Updated PaymentRecord for session: {}", session.getId());
                } else {
                    // Create new record if it doesn't exist (fallback)
                    log.warn("⚠️ PaymentRecord not found for session {}, creating new one", session.getId());
                    PaymentRecord record = PaymentRecord.builder()
                            .sessionId(session.getId())
                            .paymentIntentId(session.getPaymentIntent())
                            .customerEmail(session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null)
                            .amountTotal(session.getAmountTotal() != null ? 
                                BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100)) : null) // Convert cents to euros
                            .currency(session.getCurrency())
                            .status(PaymentRecord.PaymentStatus.COMPLETED)
                            .stripeCreatedAt(completedTime)
                            .stripeExpiresAt(convertToLocalDateTime(session.getExpiresAt()))
                            .paymentStatus(session.getPaymentStatus())
                            .build();

                    paymentRecordRepository.save(record);
                    log.info("💾 Created new PaymentRecord for session: {}", session.getId());
                }

            } catch (Exception e) {
                log.error("❌ Failed to update PaymentRecord: {}", e.getMessage(), e);
            }

        } else {
            log.warn("⚠️ Event data object deserialization failed");
        }
    }

    public PaymentResponseDTO retrieveSession(String sessionId) throws StripeException {
        log.info("Retrieving session with ID: {}", sessionId);
        Stripe.apiKey = secretKey;
        
        try {
            Session session = Session.retrieve(sessionId);
            return mapSessionToResponceDTO(session);
        } catch (StripeException e) {
            log.error("Error retrieving session: {}", e.getMessage());
            throw e;
        }
    }

    public PaymentResponseDTO expireSession(String sessionId) throws StripeException {
        log.info("Expiring session with ID: {}", sessionId);
        Stripe.apiKey = secretKey;
        
        try {
            Session session = Session.retrieve(sessionId);
            Session expiredSession = session.expire();
            return mapSessionToResponceDTO(expiredSession);
        } catch (StripeException e) {
            log.error("Error expiring session: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Validates and ensures that the request currency is EUR
     * If currency is null, sets it to EUR. If it's not EUR, throws exception.
     * @param request CheckoutRequest to validate and potentially modify
     * @throws IllegalArgumentException if currency is explicitly set to non-EUR
     */
    private void validateEuroCurrency(CheckoutRequest request) {
        if (request.getCurrency() == null) {
            // Default to EUR if not specified
            request.setCurrency("eur");
            log.info("Currency not specified, defaulting to EUR");
        } else if (!"eur".equalsIgnoreCase(request.getCurrency())) {
            throw new IllegalArgumentException(
                String.format("Currency must be 'eur' - only Euro payments are supported. Received: '%s'. All amounts must be in euros and will be displayed in euros in the Stripe dashboard.", 
                request.getCurrency()));
        }
    }
}
