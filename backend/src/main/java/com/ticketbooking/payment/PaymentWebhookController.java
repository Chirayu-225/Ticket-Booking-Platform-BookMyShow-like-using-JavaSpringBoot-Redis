package com.ticketbooking.payment;

import com.ticketbooking.booking.HoldNoLongerValidException;
import com.ticketbooking.booking.PaymentConfirmationService;
import com.ticketbooking.domain.Hold;
import com.ticketbooking.domain.HoldRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Razorpay's server-to-server notification. The browser callback in
 * CheckoutView normally confirms the booking first; this webhook is the
 * backstop for when the user closes the tab right after paying. Both paths
 * go through PaymentConfirmationService keyed by the payment id, so whichever
 * arrives second is a no-op.
 *
 * Response codes matter: Razorpay retries non-2xx deliveries. Bad signature
 * -> 400 (reject). A payment we can't turn into a booking -> 200 (retrying
 * won't help; it's a refund case). Unexpected errors -> 500 (do retry).
 */
@RestController
@RequestMapping("/api/webhooks/razorpay")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final RazorpayService razorpayService;
    private final PaymentConfirmationService paymentConfirmationService;
    private final HoldRepository holdRepository;

    public PaymentWebhookController(RazorpayService razorpayService,
                                    PaymentConfirmationService paymentConfirmationService,
                                    HoldRepository holdRepository) {
        this.razorpayService = razorpayService;
        this.paymentConfirmationService = paymentConfirmationService;
        this.holdRepository = holdRepository;
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {

        if (!razorpayService.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected webhook with invalid signature (event {})", eventId);
            return ResponseEntity.badRequest().body("invalid signature");
        }

        JSONObject body = new JSONObject(rawBody);
        if (!"payment.captured".equals(body.optString("event"))) {
            return ResponseEntity.ok("ignored");
        }

        JSONObject payment = body.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        String razorpayOrderId = payment.optString("order_id", null);
        String razorpayPaymentId = payment.getString("id");

        Hold hold = razorpayOrderId == null ? null : holdRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (hold == null) {
            log.warn("Webhook for unknown Razorpay order {} (payment {})", razorpayOrderId, razorpayPaymentId);
            return ResponseEntity.ok("no matching hold");
        }

        try {
            paymentConfirmationService.confirm(hold.getId(), razorpayPaymentId);
            return ResponseEntity.ok("processed");
        } catch (HoldNoLongerValidException e) {
            log.warn("REFUND NEEDED: {}", e.getMessage());
            return ResponseEntity.ok("refund required");
        }
    }
}
