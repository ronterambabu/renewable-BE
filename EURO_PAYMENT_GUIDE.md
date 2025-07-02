/**
 * EURO PAYMENT SYSTEM - API USAGE GUIDE
 * 
 * The payment system has been configured to ONLY accept EUR currency.
 * All payments will be processed in euros and displayed in euros in the Stripe dashboard.
 */

# API Endpoints

## 1. Create Checkout Session (with automatic validation)
POST /api/payment/create-checkout-session

### Request Body:
```json
{
  "productName": "Conference Registration",
  "description": "Annual conference registration fee",
  "orderReference": "ORDER-2024-001",
  "unitAmount": 4500,          // Amount in euro cents (4500 = €45.00)
  "quantity": 1,
  "currency": "eur",           // OPTIONAL: defaults to "eur" if not provided
  "successUrl": "https://yoursite.com/success",
  "cancelUrl": "https://yoursite.com/cancel", 
  "customerEmail": "user@example.com",
  "pricingConfigId": 1         // OPTIONAL: if provided, enables strict price validation
}
```

### Behavior:
- If `pricingConfigId` is provided: Auto-routes to validated checkout with price verification
- If `pricingConfigId` is null: Uses standard checkout (still EUR-only)
- If `currency` is null: Automatically sets to "eur"
- If `currency` is not "eur": Returns 400 Bad Request error

## 2. Create Validated Checkout Session (with strict validation)
POST /api/payment/create-validated-checkout-session

### Required Fields:
- `pricingConfigId`: Must be provided
- `currency`: Must be "eur" (or can be omitted to default to "eur")
- `unitAmount`: Must match the pricing config total price in cents

### Validation:
1. Fetches PricingConfig by ID
2. Validates that `unitAmount * quantity` equals `pricingConfig.totalPrice * 100`
3. Ensures currency is EUR
4. Creates Stripe session with validated amounts

# Currency Enforcement

## What's Enforced:
- ALL checkout requests must use EUR currency
- ALL Stripe sessions are created with EUR currency
- ALL database records store currency as "eur"
- ALL payment amounts are stored in euros (BigDecimal)
- ALL Stripe dashboard reports show EUR amounts

## Error Handling:
- Invalid currency: HTTP 400 with message explaining EUR requirement
- Price mismatch: HTTP 400 with expected vs actual amounts in euros
- Missing pricing config: HTTP 400 with config not found message

# Examples

## Valid Request (currency specified):
```json
{
  "productName": "Workshop Registration",
  "unitAmount": 7500,     // €75.00
  "quantity": 1,
  "currency": "eur",      // Explicitly set to EUR
  "customerEmail": "participant@example.com",
  "successUrl": "https://workshop.com/success",
  "cancelUrl": "https://workshop.com/cancel"
}
```

## Valid Request (currency omitted - defaults to EUR):
```json
{
  "productName": "Conference Registration", 
  "unitAmount": 4500,     // €45.00
  "quantity": 1,
  // "currency" omitted - will default to "eur"
  "customerEmail": "attendee@example.com",
  "successUrl": "https://conference.com/success",
  "cancelUrl": "https://conference.com/cancel"
}
```

## Invalid Request (wrong currency):
```json
{
  "productName": "Registration",
  "unitAmount": 4500,
  "quantity": 1,
  "currency": "usd",      // ❌ ERROR: Only EUR supported
  "customerEmail": "user@example.com"
}
```
**Response:** 400 Bad Request - "Currency must be 'eur' - only Euro payments are supported. Received: 'usd'..."

## Request with Price Validation:
```json
{
  "productName": "Premium Registration",
  "unitAmount": 9500,     // €95.00 in cents
  "quantity": 1,
  "currency": "eur",
  "pricingConfigId": 5,   // Must exist and match the amount
  "customerEmail": "premium@example.com",
  "successUrl": "https://site.com/success",
  "cancelUrl": "https://site.com/cancel"
}
```

# Stripe Dashboard

When you view payments in the Stripe dashboard, you will see:
- Currency: EUR (€)
- All amounts displayed in euros (e.g., €45.00, €75.00)
- Transaction fees in euros
- Revenue reports in euros
- Customer payment history in euros

# Database Storage

- `PaymentRecord.currency`: Always "eur"
- `PaymentRecord.amountTotal`: BigDecimal in euros (e.g., 45.00)
- `PricingConfig.totalPrice`: BigDecimal in euros
- All payment statistics and reports show euro values

# Admin Endpoints

GET /api/payments/statistics
- Returns total amounts in euros
- All monetary values are BigDecimal euro amounts

GET /api/payments/customer/{email}
- Payment history in euros
- Euro-based reporting for customer analytics
