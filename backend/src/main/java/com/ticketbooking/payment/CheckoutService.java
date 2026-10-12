package com.ticketbooking.payment;

import com.razorpay.Order;
import com.razorpay.RazorpayException;
import com.ticketbooking.booking.HoldNoLongerValidException;
import com.ticketbooking.booking.PaymentConfirmationService;
import com.ticketbooking.booking.Pricing;
import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.Hold;
import com.ticketbooking.domain.HoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Everything between "Proceed to pay" and a confirmed booking. */
@Service
public class CheckoutService {

    private final RazorpayService razorpayService;
    private final HoldRepository holdRepository;
    private final PaymentConfirmationService paymentConfirmationService;

    public CheckoutService(RazorpayService razorpayService, HoldRepository holdRepository,
                           PaymentConfirmationService paymentConfirmationService) {
        this.razorpayService = razorpayService;
        this.holdRepository = holdRepository;
        this.paymentConfirmationService = paymentConfirmationService;
    }

    public record CheckoutSession(String orderId, long amountPaise, String keyId) {}

    /**
     * Returns the Razorpay order for this hold, creating it on first use.
     * Re-using the order matters: if a page refresh created a second order,
     * a payment made against the first one would arrive for an order id no
     * hold recognises any more — money taken, no booking.
     */
    @Transactional
    public CheckoutSession startCheckout(Long holdId, Long userId) throws RazorpayException {
        Hold hold = ownedActiveHold(holdId, userId);
        long amountPaise = Pricing.toPaise(Pricing.total(hold.getShow(), hold.getSeats()));
        if (hold.getRazorpayOrderId() == null) {
            Order order = razorpayService.createOrder(amountPaise, "hold-" + hold.getId());
            hold.setRazorpayOrderId(order.get("id"));
        }
        return new CheckoutSession(hold.getRazorpayOrderId(), amountPaise, razorpayService.getKeyId());
    }

    /**
     * Called with what Razorpay's checkout widget hands the browser on
     * success. The signature check is what makes it safe to trust.
     */
    public Booking completePayment(Long holdId, Long userId, String orderId, String paymentId, String signature) {
        if (!razorpayService.verifyCheckoutSignature(orderId, paymentId, signature)) {
            throw new IllegalArgumentException("Payment signature did not verify");
        }
        Hold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new HoldNoLongerValidException("Hold " + holdId + " not found"));
        if (!orderId.equals(hold.getRazorpayOrderId())) {
            throw new IllegalArgumentException("Payment is for a different order");
        }
        // Ownership via a scalar query: outside a transaction, so no lazy proxies.
        if (!holdRepository.findOwnerIdById(holdId).map(userId::equals).orElse(false)) {
            throw new IllegalArgumentException("Hold belongs to a different user");
        }
        return paymentConfirmationService.confirm(holdId, paymentId);
    }

    private Hold ownedActiveHold(Long holdId, Long userId) {
        Hold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new HoldNoLongerValidException("This hold no longer exists."));
        if (!hold.getUser().getId().equals(userId)) {
            throw new HoldNoLongerValidException("This hold belongs to someone else.");
        }
        if (hold.getStatus() != Hold.Status.ACTIVE || hold.getExpiresAt().isBefore(Instant.now())) {
            throw new HoldNoLongerValidException("Your seat hold has expired. Please pick your seats again.");
        }
        return hold;
    }
}
