# Webhook Database Update Testing Guide

## 🔍 **Current Issue Analysis**

Based on your logs:
```
✅ Webhook signature verified successfully. Event type: payment_intent.succeeded
Processing webhook event of type: payment_intent.succeeded
✅ Payment Intent succeeded: pi_xxx for amount: 45.00 eur
```

The webhook is working but not updating the database. Here's how to fix and test it:

## 🛠 **Step 1: Check Your Environment Variables**

Make sure these are set correctly:
```powershell
# Set these in PowerShell before starting the app
$env:STRIPE_SECRET_KEY="sk_test_your_actual_stripe_secret_key"
$env:STRIPE_WEBHOOK_SECRET="whsec_your_actual_webhook_secret"
```

## 🧪 **Step 2: Test Database Connection**

1. **Check if PaymentRecord exists before webhook:**
   ```sql
   SELECT * FROM payment_record WHERE status = 'PENDING' ORDER BY created_at DESC LIMIT 5;
   ```

2. **Check if PaymentRecord gets updated after webhook:**
   ```sql
   SELECT * FROM payment_record WHERE status = 'COMPLETED' ORDER BY updated_at DESC LIMIT 5;
   ```

## 🔧 **Step 3: Manual Database Update Test**

Create a test endpoint to manually trigger the webhook processing logic:

### Test URL: `POST /api/payment/test-webhook-update`

```bash
curl -X POST http://localhost:8901/api/payment/test-webhook-update \
  -H "Content-Type: application/json" \
  -d '{"paymentIntentId": "pi_test_12345", "amount": 4500}'
```

## 📋 **Step 4: Expected Database Flow**

### **Initial State (After checkout session creation):**
```sql
INSERT INTO payment_record (
    session_id, 
    customer_email, 
    amount_total, 
    currency, 
    status, 
    created_at
) VALUES (
    'cs_test_xxx', 
    'user@example.com', 
    45.00, 
    'eur', 
    'PENDING', 
    NOW()
);
```

### **After payment_intent.succeeded webhook:**
```sql
UPDATE payment_record 
SET 
    payment_intent_id = 'pi_test_xxx',
    status = 'COMPLETED',
    updated_at = NOW()
WHERE 
    (payment_intent_id = 'pi_test_xxx' OR status = 'PENDING')
    AND amount_total = 45.00;
```

## 🚨 **Common Issues & Solutions**

| Issue | Cause | Solution |
|-------|-------|----------|
| No database update | PaymentRecord not found | Check if record exists with correct amount |
| Multiple records updated | Multiple PENDING records | Use more specific WHERE clause |
| Wrong amount | Currency conversion error | Verify amount matches (euros vs cents) |
| Transaction rollback | Database constraint violation | Check foreign key constraints |

## 🔍 **Step 5: Debug Logging**

Add these to your application.properties for detailed logging:
```properties
logging.level.com.zn.payment.service.StripeService=DEBUG
logging.level.org.springframework.transaction=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

## 🧪 **Step 6: Complete Test Scenario**

1. **Create checkout session:**
   ```bash
   curl -X POST http://localhost:8901/api/payment/create-checkout-session \
     -H "Content-Type: application/json" \
     -d '{
       "productName": "Test Product",
       "unitAmount": 45.00,
       "quantity": 1,
       "currency": "eur",
       "customerEmail": "test@example.com",
       "successUrl": "https://example.com/success",
       "cancelUrl": "https://example.com/cancel"
     }'
   ```

2. **Check database - should see PENDING record:**
   ```sql
   SELECT * FROM payment_record WHERE customer_email = 'test@example.com';
   ```

3. **Complete payment in Stripe** (or simulate webhook)

4. **Check database - should see COMPLETED record:**
   ```sql
   SELECT * FROM payment_record WHERE customer_email = 'test@example.com' AND status = 'COMPLETED';
   ```

## 🎯 **Expected Results**

### **Before Payment:**
- Status: `PENDING`
- PaymentIntentId: `null`
- Amount: `45.00`

### **After Payment:**
- Status: `COMPLETED` 
- PaymentIntentId: `pi_xxx`
- Amount: `45.00`
- UpdatedAt: Recent timestamp

## 📞 **If Still Not Working**

1. Check application logs for errors
2. Verify webhook endpoint is receiving correct data
3. Ensure database connection is working
4. Check if transactions are being rolled back
5. Verify amount matching logic (euros vs cents)
