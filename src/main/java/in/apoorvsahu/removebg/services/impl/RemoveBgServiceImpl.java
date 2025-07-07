package in.apoorvsahu.removebg.services.impl;

import in.apoorvsahu.removebg.clients.ClipdropClient;
import in.apoorvsahu.removebg.exceptions.RemoveBgServiceException;
import in.apoorvsahu.removebg.services.RemoveBgService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemoveBgServiceImpl implements RemoveBgService {

    @Value("${clipdrop.apikey}")
    private String apiKey;

    private final ClipdropClient clipdropClient;

    @Override
    public byte[] removeBackground(MultipartFile file) {
        try {
            validateApiKey();

            log.info("Processing image: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());

            byte[] result = clipdropClient.removeBackground(file, apiKey);

            if (result == null || result.length == 0) {
                throw new RemoveBgServiceException("No image data received from processing service");
            }

            log.info("Successfully processed image: {}", file.getOriginalFilename());
            return result;

        } catch (FeignException.Unauthorized e) {
            log.error("Unauthorized access to Clipdrop API: {}", e.getMessage());
            throw new RemoveBgServiceException("Invalid API configuration. Please contact support");
        } catch (FeignException.TooManyRequests e) {
            log.error("Rate limit exceeded for Clipdrop API: {}", e.getMessage());
            throw new RemoveBgServiceException("Service is busy. Please try again in a few minutes");
        } catch (FeignException.BadRequest e) {
            log.error("Bad request to Clipdrop API: {}", e.getMessage());
            throw new RemoveBgServiceException("Invalid image format. Please upload a valid image file");
        } catch (FeignException.InternalServerError e) {
            log.error("Internal server error from Clipdrop API: {}", e.getMessage());
            throw new RemoveBgServiceException("Image processing service is temporarily unavailable");
        } catch (FeignException e) {
            log.error("Feign client error: Status {} - {}", e.status(), e.getMessage());
            throw new RemoveBgServiceException("Failed to process image. Please try again later");
        } catch (Exception e) {
            log.error("Unexpected error while processing image: ", e);
            throw new RemoveBgServiceException("An unexpected error occurred while processing your image");
        }
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("Clipdrop API key is not configured");
            throw new RemoveBgServiceException("Service configuration error. Please contact support");
        }
    }
}