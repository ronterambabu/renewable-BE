# Webhook Setup and Testing Guide

## ❌ Current Issue
```
Webhook signature verification failed: No signatures found matching the expected signature for payload
```

## 🔧 How to Fix This Issue

### 1. **Get the Correct Webhook Secret from Stripe Dashboard**

1. Go to [Stripe Dashboard](https://dashboard.stripe.com)
2. Navigate to **Developers > Webhooks**
3. Click on your webhook endpoint (or create one if it doesn't exist)
4. Click **"Reveal"** next to the **Signing secret**
5. Copy the secret that starts with `whsec_...`

### 2. **Update Your .env File**

```bash
# Your current .env file should look like this:
STRIPE_SECRET_KEY=sk_test_YOUR_STRIPE_SECRET_KEY_HERE
STRIPE_WEBHOOK_SECRET=whsec_YOUR_ACTUAL_WEBHOOK_SECRET_HERE
```

**❗ IMPORTANT**: The webhook secret MUST start with `whsec_`, NOT `rk_test_`

### 3. **Set Environment Variables (Windows)**

```powershell
# In PowerShell, set environment variables:
$env:STRIPE_SECRET_KEY="sk_test_YOUR_STRIPE_SECRET_KEY_HERE"
$env:STRIPE_WEBHOOK_SECRET="whsec_YOUR_ACTUAL_WEBHOOK_SECRET_HERE"
```

### 4. **Test the Webhook Locally**

#### Option A: Using Stripe CLI (Recommended)
```bash
# Install Stripe CLI if you haven't already
# Download from: https://stripe.com/docs/stripe-cli

# Login to Stripe
stripe login

# Forward webhook events to your local server
stripe listen --forward-to localhost:8901/api/payment/webhook
```

#### Option B: Using ngrok for external testing
```bash
# Install ngrok
# Download from: https://ngrok.com/

# Expose your local server
ngrok http 8901

# Use the ngrok URL in your Stripe webhook endpoint
# Example: https://abc123.ngrok.io/api/payment/webhook
```

### 5. **Webhook Endpoint Configuration in Stripe**

When setting up your webhook endpoint in Stripe Dashboard:

**Endpoint URL**: `https://yourdomain.com/api/payment/webhook`
**Events to send**:
- `checkout.session.completed`
- `payment_intent.succeeded`
- `payment_intent.payment_failed`

### 6. **Testing the Complete Flow**

1. **Start your application**: `mvn spring-boot:run`
2. **Create a checkout session** using your API
3. **Complete the payment** in Stripe's test checkout
4. **Check your application logs** for webhook processing
5. **Verify database updates** - PaymentRecord should be updated to COMPLETED status

### 7. **Expected Log Output**

When working correctly, you should see:
```
✅ Webhook signature verified successfully. Event type: checkout.session.completed
✅ Payment successful for session: cs_test_...
💾 Updated PaymentRecord for session: cs_test_...
```

### 8. **Common Issues and Solutions**

| Issue | Solution |
|-------|----------|
| `No signatures found` | Wrong webhook secret - get the correct one from Stripe Dashboard |
| `Invalid webhook secret format` | Secret should start with `whsec_` |
| `Webhook endpoint secret is not configured` | Set the STRIPE_WEBHOOK_SECRET environment variable |
| `404 Not Found` | Check the webhook URL is correct: `/api/payment/webhook` |

### 9. **Database Updates**

When the webhook works correctly:
- PaymentRecord status changes from `PENDING` to `COMPLETED`
- PaymentIntent ID is populated
- Timestamps are updated

## 🚀 Next Steps

1. Get the correct webhook secret from Stripe Dashboard
2. Update your .env file
3. Restart your application
4. Test with Stripe CLI or ngrok
5. Verify database updates
