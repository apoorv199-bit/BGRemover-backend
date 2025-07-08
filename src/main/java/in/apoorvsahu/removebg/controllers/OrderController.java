package in.apoorvsahu.removebg.controllers;

import com.razorpay.Order;
import com.razorpay.RazorpayException;
import in.apoorvsahu.removebg.dtos.RazorpayOrderDto;
import in.apoorvsahu.removebg.exceptions.InvalidPlanException;
import in.apoorvsahu.removebg.exceptions.OrderNotFoundException;
import in.apoorvsahu.removebg.exceptions.PaymentProcessingException;
import in.apoorvsahu.removebg.response.RemoveBgResponse;
import in.apoorvsahu.removebg.services.OrderService;
import in.apoorvsahu.removebg.services.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final RazorpayService razorpayService;

    @PostMapping
    public ResponseEntity<RemoveBgResponse> createOrder(@RequestParam String planId, Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null || authentication.getName().isEmpty()) {
                return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }

            String clerkId = authentication.getName();
            Order order = orderService.createOrder(planId.trim(), clerkId);
            RazorpayOrderDto responseDto = convertToDto(order);

            log.info("Order created successfully for user: {} with plan: {}", clerkId, planId);
            return buildSuccessResponse(responseDto, "Order created successfully", HttpStatus.CREATED);

        } catch (InvalidPlanException e) {
            log.warn("Invalid plan selected: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid plan selected. Please choose a valid plan");
        } catch (PaymentProcessingException e) {
            log.error("Payment processing error: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Payment service is temporarily unavailable. Please try again later");
        } catch (RazorpayException e) {
            log.error("Razorpay error while creating order: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway error. Please try again later");
        } catch (Exception e) {
            log.error("Unexpected error while creating order: ", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later");
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<RemoveBgResponse> verifyOrder(@RequestBody Map<String, Object> request) {
        try {
            if (request == null || request.isEmpty()) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST, "Payment verification data is required");
            }

            if (!request.containsKey("razorpay_order_id") || request.get("razorpay_order_id") == null) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST, "Order ID is required for verification");
            }

            String razorpayOrderId = request.get("razorpay_order_id").toString();

            if (razorpayOrderId.trim().isEmpty()) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST, "Valid order ID is required");
            }

            Map<String, Object> verificationResult = razorpayService.verifyPayment(razorpayOrderId);

            boolean success = (Boolean) verificationResult.getOrDefault("success", false);
            String message = (String) verificationResult.getOrDefault("message", "Payment verification completed");

            if (success) {
                log.info("Payment verified successfully for order: {}", razorpayOrderId);
                return buildSuccessResponse(verificationResult, message, HttpStatus.OK);
            } else {
                log.warn("Payment verification failed for order: {}", razorpayOrderId);
                return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, message);
            }

        } catch (OrderNotFoundException e) {
            log.warn("Order not found for verification: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Order not found. Please contact support if you believe this is an error");
        } catch (PaymentProcessingException e) {
            log.error("Payment processing error during verification: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Payment verification failed. Please try again later");
        } catch (RazorpayException e) {
            log.error("Razorpay error during payment verification: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway error. Please try again later");
        } catch (Exception e) {
            log.error("Unexpected error during payment verification: ", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Payment verification failed. Please contact support");
        }
    }

    private RazorpayOrderDto convertToDto(Order order) {
        try {
            return RazorpayOrderDto.builder()
                    .id(order.get("id"))
                    .entity(order.get("entity"))
                    .amount(order.get("amount"))
                    .currency(order.get("currency"))
                    .status(order.get("status"))
                    .created_at(order.get("created_at"))
                    .receipt(order.get("receipt"))
                    .build();
        } catch (Exception e) {
            log.error("Error converting order to DTO: ", e);
            throw new PaymentProcessingException("Failed to process order details");
        }
    }

    private ResponseEntity<RemoveBgResponse> buildSuccessResponse(Object data, String message) {
        return buildSuccessResponse(data, message, HttpStatus.OK);
    }

    private ResponseEntity<RemoveBgResponse> buildSuccessResponse(Object data, String message, HttpStatus status) {
        RemoveBgResponse response = RemoveBgResponse.builder()
                .success(true)
                .data(data)
                .message(message)
                .statusCode(status)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<RemoveBgResponse> buildErrorResponse(HttpStatus status, String message) {
        RemoveBgResponse response = RemoveBgResponse.builder()
                .success(false)
                .data(null)
                .message(message)
                .statusCode(status)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}