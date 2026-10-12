package com.ticketbooking.payment;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around Razorpay's SDK. Two signature checks live here:
 *  - the checkout callback signature (HMAC of order_id|payment_id with the
 *    API key secret), which proves the browser's "payment succeeded" message
 *    really came from Razorpay;
 *  - the webhook signature (HMAC of the raw body with the webhook secret).
 * Without them anyone could tell the server "I paid" and get free seats.
 */
@Service
public class RazorpayService {

    private final RazorpayClient client;
    private final RazorpayProperties properties;

    public RazorpayService(RazorpayProperties properties) throws RazorpayException {
        this.properties = properties;
        this.client = new RazorpayClient(properties.getKeyId(), properties.getKeySecret());
    }

    public boolean isConfigured() {
        String key = properties.getKeyId();
        return key != null && key.startsWith("rzp_") && !key.contains("placeholder");
    }

    public String getKeyId() {
        return properties.getKeyId();
    }

    public Order createOrder(long amountPaise, String receiptId) throws RazorpayException {
        JSONObject request = new JSONObject();
        request.put("amount", amountPaise);
        request.put("currency", "INR");
        request.put("receipt", receiptId);
        request.put("payment_capture", 1);
        return client.orders.create(request);
    }

    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            return Utils.verifyPaymentSignature(attributes, properties.getKeySecret());
        } catch (RazorpayException e) {
            return false;
        }
    }

    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            return Utils.verifyWebhookSignature(payload, signature, properties.getWebhookSecret());
        } catch (RazorpayException e) {
            return false;
        }
    }
}
