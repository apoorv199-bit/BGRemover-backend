package in.apoorvsahu.removebg.controllers;

import in.apoorvsahu.removebg.dtos.UserDto;
import in.apoorvsahu.removebg.exceptions.UserNotFoundException;
import in.apoorvsahu.removebg.exceptions.ValidationException;
import in.apoorvsahu.removebg.response.RemoveBgResponse;
import in.apoorvsahu.removebg.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<RemoveBgResponse> createOrUpdateUser(@RequestBody UserDto userDto, Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }

            if (!authentication.getName().equals(userDto.getClerkId())) {
                return buildErrorResponse(HttpStatus.FORBIDDEN, "You don't have permission to perform this action");
            }

            validateUserDto(userDto);

            UserDto user = userService.saveUser(userDto);

            return buildSuccessResponse(user, "User saved successfully");

        } catch (ValidationException e) {
            log.warn("Validation error for user: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (UserNotFoundException e) {
            log.warn("User not found: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.NOT_FOUND, "User not found");
        } catch (Exception e) {
            log.error("Unexpected error while saving user: ", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later");
        }
    }

    @GetMapping("/credits")
    public ResponseEntity<RemoveBgResponse> getUserCredits(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null || authentication.getName().isEmpty()) {
                return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }

            String clerkId = authentication.getName();
            UserDto existingUser = userService.getUserByClerkId(clerkId);

            Map<String, Integer> creditsData = new HashMap<>();
            creditsData.put("credits", existingUser.getCredits());

            return buildSuccessResponse(creditsData, "Credits retrieved successfully");

        } catch (UserNotFoundException e) {
            log.warn("User not found for credits request: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.NOT_FOUND, "User account not found");
        } catch (Exception e) {
            log.error("Unexpected error while fetching user credits: ", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to fetch credits. Please try again later");
        }
    }

    private void validateUserDto(UserDto userDto) {
        if (userDto == null) {
            throw new ValidationException("User data is required");
        }
        if (userDto.getClerkId() == null || userDto.getClerkId().trim().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
        if (userDto.getEmail() == null || userDto.getEmail().trim().isEmpty()) {
            throw new ValidationException("Email is required");
        }
        if (userDto.getFirstName() == null || userDto.getFirstName().trim().isEmpty()) {
            throw new ValidationException("First name is required");
        }
        if (userDto.getLastName() == null || userDto.getLastName().trim().isEmpty()) {
            throw new ValidationException("Last name is required");
        }
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