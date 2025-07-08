package in.apoorvsahu.removebg.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.apoorvsahu.removebg.dtos.UserDto;
import in.apoorvsahu.removebg.exceptions.UserNotFoundException;
import in.apoorvsahu.removebg.exceptions.UserServiceException;
import in.apoorvsahu.removebg.exceptions.ValidationException;
import in.apoorvsahu.removebg.exceptions.WebhookException;
import in.apoorvsahu.removebg.response.RemoveBgResponse;
import in.apoorvsahu.removebg.services.UserService;
import in.apoorvsahu.removebg.services.WebhookSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class ClerkWebhookController {

    private final UserService userService;
    private final WebhookSignatureService webhookSignatureService;

    @PostMapping("/clerk")
    public ResponseEntity<RemoveBgResponse> handleClerkWebhook(@RequestHeader(value = "svix-id", required = false) String svixId,
                                                               @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
                                                               @RequestHeader(value = "svix-signature", required = false) String svixSignature,
                                                               @RequestBody String payload) {
        try {
            validateWebhookHeaders(svixId, svixTimestamp, svixSignature);

            boolean isValid = webhookSignatureService.verifyWebhookSignature(svixId, svixTimestamp, svixSignature, payload);
            if (!isValid) {
                log.warn("Invalid webhook signature received");
                return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(payload);

            String eventType = rootNode.path("type").asText();

            if (eventType == null || eventType.isEmpty()) {
                log.warn("Missing event type in webhook payload");
                return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid webhook payload: missing event type");
            }

            log.info("Processing webhook event: {}", eventType);

            switch (eventType) {
                case "user.created":
                    handleUserCreated(rootNode.path("data"));
                    break;
                case "user.updated":
                    handleUserUpdated(rootNode.path("data"));
                    break;
                case "user.deleted":
                    handleUserDeleted(rootNode.path("data"));
                    break;
                default:
                    log.warn("Unsupported webhook event type: {}", eventType);
                    return buildErrorResponse(HttpStatus.BAD_REQUEST, "Unsupported event type");
            }

            log.info("Successfully processed webhook event: {}", eventType);
            return buildSuccessResponse(null, "Webhook processed successfully");

        } catch (WebhookException | ValidationException e) {
            log.error("Webhook validation error: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (UserServiceException e) {
            log.error("User service error during webhook processing: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process user data");
        } catch (Exception e) {
            log.error("Unexpected error while processing webhook: ", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Webhook processing failed");
        }
    }

    private void validateWebhookHeaders(String svixId, String svixTimestamp, String svixSignature) {
        if (svixId == null || svixId.trim().isEmpty()) {
            throw new WebhookException("Missing svix-id header");
        }
        if (svixTimestamp == null || svixTimestamp.trim().isEmpty()) {
            throw new WebhookException("Missing svix-timestamp header");
        }
        if (svixSignature == null || svixSignature.trim().isEmpty()) {
            throw new WebhookException("Missing svix-signature header");
        }
    }

    private void handleUserDeleted(JsonNode data) {
        try {
            String clerkId = extractClerkId(data);
            userService.deleteUserByClerkId(clerkId);
            log.info("Successfully deleted user with clerkId: {}", clerkId);
        } catch (Exception e) {
            log.error("Error handling user deletion: ", e);
            throw new UserServiceException("Failed to delete user");
        }
    }

    private void handleUserUpdated(JsonNode data) {
        try {
            String clerkId = extractClerkId(data);
            UserDto existingUser = userService.getUserByClerkId(clerkId);

            updateUserFromWebhookData(existingUser, data);

            userService.saveUser(existingUser);
            log.info("Successfully updated user with clerkId: {}", clerkId);
        } catch (UserNotFoundException e) {
            log.warn("User not found for update, creating new user instead");
            handleUserCreated(data);
        } catch (Exception e) {
            log.error("Error handling user update: ", e);
            throw new UserServiceException("Failed to update user");
        }
    }

    private void handleUserCreated(JsonNode data) {
        try {
            UserDto newUser = buildUserFromWebhookData(data);
            userService.saveUser(newUser);
            log.info("Successfully created user with clerkId: {}", newUser.getClerkId());
        } catch (Exception e) {
            log.error("Error handling user creation: ", e);
            throw new UserServiceException("Failed to create user");
        }
    }

    private UserDto buildUserFromWebhookData(JsonNode data) {
        return UserDto.builder()
                .clerkId(extractClerkId(data))
                .email(extractEmail(data))
                .firstName(extractFirstName(data))
                .lastName(extractLastName(data))
                .photoUrl(extractPhotoUrl(data))
                .build();
    }

    private void updateUserFromWebhookData(UserDto user, JsonNode data) {
        user.setEmail(extractEmail(data));
        user.setFirstName(extractFirstName(data));
        user.setLastName(extractLastName(data));
        user.setPhotoUrl(extractPhotoUrl(data));
    }

    private String extractClerkId(JsonNode data) {
        String clerkId = data.path("id").asText();
        if (clerkId == null || clerkId.trim().isEmpty()) {
            throw new ValidationException("Missing user ID in webhook data");
        }
        return clerkId;
    }

    private String extractEmail(JsonNode data) {
        JsonNode emailAddresses = data.path("email_addresses");
        if (emailAddresses.isArray() && !emailAddresses.isEmpty()) {
            String email = emailAddresses.get(0).path("email_address").asText();
            if (email == null || email.trim().isEmpty()) {
                throw new ValidationException("Missing email address in webhook data");
            }
            return email;
        }
        throw new ValidationException("No email addresses found in webhook data");
    }

    private String extractFirstName(JsonNode data) {
        String firstName = data.path("first_name").asText();
        return firstName != null && !firstName.trim().isEmpty() ? firstName : "Unknown";
    }

    private String extractLastName(JsonNode data) {
        String lastName = data.path("last_name").asText();
        return lastName != null && !lastName.trim().isEmpty() ? lastName : "User";
    }

    private String extractPhotoUrl(JsonNode data) {
        String photoUrl = data.path("image_url").asText();
        return photoUrl != null && !photoUrl.trim().isEmpty() ? photoUrl : null;
    }

    private ResponseEntity<RemoveBgResponse> buildSuccessResponse(Object data, String message) {
        RemoveBgResponse response = RemoveBgResponse.builder()
                .success(true)
                .data(data)
                .message(message)
                .statusCode(HttpStatus.OK)
                .build();
        return ResponseEntity.ok(response);
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