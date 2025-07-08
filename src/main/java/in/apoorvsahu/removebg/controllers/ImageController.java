package in.apoorvsahu.removebg.controllers;

import in.apoorvsahu.removebg.dtos.UserDto;
import in.apoorvsahu.removebg.exceptions.InvalidFileException;
import in.apoorvsahu.removebg.exceptions.RemoveBgServiceException;
import in.apoorvsahu.removebg.response.RemoveBgResponse;
import in.apoorvsahu.removebg.services.RemoveBgService;
import in.apoorvsahu.removebg.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final RemoveBgService removeBgService;
    private final UserService userService;

    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/webp"};
    private static final long MAX_FILE_SIZE = 30 * 1024 * 1024; // 30MB

    @PostMapping("/remove-background")
    public ResponseEntity<RemoveBgResponse> removeBackground(@RequestParam("file") MultipartFile file,
                                                             Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null || authentication.getName().isEmpty()) {
                return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }

            validateFile(file);

            String clerkId = authentication.getName();
            UserDto userDto = userService.getUserByClerkId(clerkId);

            if (userDto.getCredits() == null || userDto.getCredits() <= 0) {
                Map<String, Object> creditData = new HashMap<>();
                creditData.put("creditBalance", userDto.getCredits() != null ? userDto.getCredits() : 0);

                log.warn("Insufficient credits for user: {}", authentication.getName());
                return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, "Insufficient credits to process image", creditData);
            }

            byte[] imageBytes = removeBgService.removeBackground(file);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            userDto.setCredits(userDto.getCredits() - 1);
            userService.saveUser(userDto);

            log.info("Successfully processed image for user: {}, remaining credits: {}", clerkId, userDto.getCredits());
            return buildSuccessResponse(base64Image, "Image background removed successfully");

        } catch (InvalidFileException e) {
            log.warn("Invalid file uploaded: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RemoveBgServiceException e) {
            log.error("Error processing image: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while processing image: ", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process image. Please try again later");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Please select an image file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("File size too large. Maximum allowed size is 30MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedFileType(contentType)) {
            throw new InvalidFileException("Invalid file type. Please upload JPEG, PNG, or WebP images only");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new InvalidFileException("Invalid file name");
        }
    }

    private boolean isAllowedFileType(String contentType) {
        for (String allowedType : ALLOWED_TYPES) {
            if (allowedType.equalsIgnoreCase(contentType)) {
                return true;
            }
        }
        return false;
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
        return buildErrorResponse(status, message, null);
    }

    private ResponseEntity<RemoveBgResponse> buildErrorResponse(HttpStatus status, String message, Object data) {
        RemoveBgResponse response = RemoveBgResponse.builder()
                .success(false)
                .data(data)
                .message(message)
                .statusCode(status)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}