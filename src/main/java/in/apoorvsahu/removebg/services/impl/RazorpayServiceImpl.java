package in.apoorvsahu.removebg.services.impl;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import in.apoorvsahu.removebg.Repositories.OrderRepository;
import in.apoorvsahu.removebg.dtos.UserDto;
import in.apoorvsahu.removebg.entities.OrderEntity;
import in.apoorvsahu.removebg.exceptions.OrderNotFoundException;
import in.apoorvsahu.removebg.exceptions.PaymentProcessingException;
import in.apoorvsahu.removebg.exceptions.UserNotFoundException;
import in.apoorvsahu.removebg.services.RazorpayService;
import in.apoorvsahu.removebg.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayServiceImpl implements RazorpayService {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private final OrderRepository orderRepository;
    private final UserService userService;

    @Override
    public Order createOrder(Double amount, String currency) throws RazorpayException {
        try {
            if (amount == null || amount <= 0) {
                throw new PaymentProcessingException("Invalid amount specified");
            }

            if (currency == null || currency.trim().isEmpty()) {
                throw new PaymentProcessingException("Currency is required");
            }

            if (razorpayKeyId == null || razorpayKeyId.trim().isEmpty() ||
                    razorpayKeySecret == null || razorpayKeySecret.trim().isEmpty()) {
                log.error("Razorpay credentials not configured properly");
                throw new PaymentProcessingException("Payment service configuration error");
            }

            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", Math.round(amount * 100));
            orderRequest.put("currency", currency.toUpperCase());
            orderRequest.put("receipt", "order_rcptid_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1);

            log.info("Creating Razorpay order with amount: {} {}", amount, currency);
            Order order = razorpayClient.orders.create(orderRequest);

            log.info("Razorpay order created successfully: {}", (Object) order.get("id"));
            return order;

        } catch (RazorpayException e) {
            log.error("Razorpay API error: {}", e.getMessage());
            throw new PaymentProcessingException("Payment gateway error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while creating Razorpay order: ", e);
            throw new PaymentProcessingException("Failed to create payment order");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> verifyPayment(String razorpayOrderId) throws RazorpayException {
        Map<String, Object> returnValue = new HashMap<>();

        try {
            if (razorpayOrderId == null || razorpayOrderId.trim().isEmpty()) {
                throw new PaymentProcessingException("Order ID is required for verification");
            }

            if (razorpayKeyId == null || razorpayKeyId.trim().isEmpty() ||
                    razorpayKeySecret == null || razorpayKeySecret.trim().isEmpty()) {
                log.error("Razorpay credentials not configured for verification");
                throw new PaymentProcessingException("Payment service configuration error");
            }

            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            log.info("Verifying payment for order: {}", razorpayOrderId);
            Order orderInfo = razorpayClient.orders.fetch(razorpayOrderId);

            if (orderInfo == null) {
                throw new OrderNotFoundException("Order not found in payment gateway");
            }

            String orderStatus = orderInfo.get("status") != null ? orderInfo.get("status").toString() : "";

            if ("paid".equalsIgnoreCase(orderStatus)) {
                return processPaidOrder(razorpayOrderId);
            } else {
                log.warn("Payment not completed for order: {} - Status: {}", razorpayOrderId, orderStatus);
                returnValue.put("success", false);
                returnValue.put("message", "Payment not completed. Please try again or contact support");
                return returnValue;
            }

        } catch (PaymentProcessingException | OrderNotFoundException e) {
            throw e;
        } catch (RazorpayException e) {
            log.error("Razorpay error during payment verification: {}", e.getMessage());
            throw new PaymentProcessingException("Payment gateway error during verification: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during payment verification: ", e);
            throw new PaymentProcessingException("Payment verification failed");
        }
    }

    private Map<String, Object> processPaidOrder(String razorpayOrderId) {
        Map<String, Object> returnValue = new HashMap<>();

        try {
            OrderEntity existingOrder = orderRepository.findByOrderId(razorpayOrderId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found in our records: " + razorpayOrderId));

            if (Boolean.TRUE.equals(existingOrder.getPayment())) {
                log.warn("Payment already processed for order: {}", razorpayOrderId);
                returnValue.put("success", false);
                returnValue.put("message", "Payment has already been processed for this order");
                return returnValue;
            }

            UserDto userDto = userService.getUserByClerkId(existingOrder.getClerkId());
            int newCredits = userDto.getCredits() + existingOrder.getCredits();
            userDto.setCredits(newCredits);
            userService.saveUser(userDto);

            existingOrder.setPayment(true);
            orderRepository.save(existingOrder);

            log.info("Payment processed successfully for order: {} - Added {} credits to user: {}",
                    razorpayOrderId, existingOrder.getCredits(), existingOrder.getClerkId());

            returnValue.put("success", true);
            returnValue.put("message", String.format("Payment successful! %d credits added to your account", existingOrder.getCredits()));
            returnValue.put("creditsAdded", existingOrder.getCredits());
            returnValue.put("totalCredits", newCredits);

            return returnValue;

        } catch (UserNotFoundException e) {
            log.error("User not found while processing payment: {}", e.getMessage());
            throw new PaymentProcessingException("User account not found. Please contact support");
        } catch (DataAccessException e) {
            log.error("Database error while processing payment: {}", e.getMessage());
            throw new PaymentProcessingException("Database error during payment processing. Please contact support");
        } catch (Exception e) {
            log.error("Unexpected error while processing paid order: ", e);
            throw new PaymentProcessingException("Failed to process payment. Please contact support");
        }
    }
}