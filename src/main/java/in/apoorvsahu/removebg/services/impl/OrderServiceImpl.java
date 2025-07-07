package in.apoorvsahu.removebg.services.impl;

import com.razorpay.Order;
import com.razorpay.RazorpayException;
import in.apoorvsahu.removebg.Repositories.OrderRepository;
import in.apoorvsahu.removebg.entities.OrderEntity;
import in.apoorvsahu.removebg.exceptions.InvalidPlanException;
import in.apoorvsahu.removebg.exceptions.OrderServiceException;
import in.apoorvsahu.removebg.exceptions.PaymentProcessingException;
import in.apoorvsahu.removebg.services.OrderService;
import in.apoorvsahu.removebg.services.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final RazorpayService razorpayService;
    private final OrderRepository orderRepository;

    private static final Map<String, PlanDetails> PLAN_DETAILS = Map.of(
            "Basic", new PlanDetails("Basic", 100, 499.00),
            "Premium", new PlanDetails("Premium", 250, 899.00),
            "Ultimate", new PlanDetails("Ultimate", 1000, 1499.00)
    );

    private record PlanDetails(String name, int credits, double amount) {}

    @Override
    @Transactional
    public Order createOrder(String planId, String clerkId) throws RazorpayException {
        try {
            if (planId == null || planId.trim().isEmpty()) {
                throw new InvalidPlanException("Plan ID cannot be empty");
            }

            PlanDetails details = PLAN_DETAILS.get(planId);
            if (details == null) {
                log.warn("Invalid plan requested: {}", planId);
                throw new InvalidPlanException("Selected plan is not available. Please choose from: Basic, Premium, or Ultimate");
            }

            if (clerkId == null || clerkId.trim().isEmpty()) {
                throw new OrderServiceException("User identification is required");
            }

            log.info("Creating order for user: {} with plan: {}", clerkId, planId);

            Order razorpayOrder = razorpayService.createOrder(details.amount(), "INR");

            if (razorpayOrder == null || razorpayOrder.get("id") == null) {
                throw new PaymentProcessingException("Failed to create payment order");
            }

            OrderEntity newOrder = OrderEntity.builder()
                    .clerkId(clerkId)
                    .plan(details.name())
                    .credits(details.credits())
                    .amount(details.amount())
                    .orderId(razorpayOrder.get("id").toString())
                    .payment(false)
                    .build();

            orderRepository.save(newOrder);
            log.info("Order saved successfully with ID: {}", (Object) razorpayOrder.get("id"));

            return razorpayOrder;

        } catch (InvalidPlanException | PaymentProcessingException | OrderServiceException e) {
            throw e;
        } catch (RazorpayException e) {
            log.error("Razorpay error while creating order: {}", e.getMessage());
            throw new PaymentProcessingException("Payment gateway error: " + e.getMessage());
        } catch (DataAccessException e) {
            log.error("Database error while creating order: {}", e.getMessage());
            throw new OrderServiceException("Failed to save order details. Please try again later");
        } catch (Exception e) {
            log.error("Unexpected error while creating order: ", e);
            throw new OrderServiceException("An unexpected error occurred while creating the order");
        }
    }

    public Map<String, PlanDetails> getAvailablePlans() {
        return PLAN_DETAILS;
    }


    public boolean isValidPlan(String planId) {
        return planId != null && PLAN_DETAILS.containsKey(planId);
    }
}