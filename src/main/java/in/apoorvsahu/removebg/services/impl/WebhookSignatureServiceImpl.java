package in.apoorvsahu.removebg.services.impl;

import in.apoorvsahu.removebg.services.WebhookSignatureService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Service
@Slf4j
public class WebhookSignatureServiceImpl implements WebhookSignatureService {

    @Value("${clerk.webhook.secret}")
    private String webhookSecret;

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long TIMESTAMP_TOLERANCE = 300;

    @Override
    public boolean verifyWebhookSignature(String svixId, String svixTimestamp, String svixSignature, String payload) {
        try {
            // Check if webhook secret is configured
            if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
                log.warn("Webhook secret not configured - signature verification disabled");
                return false;
            }

            // Verify timestamp to prevent replay attacks
            if (!isTimestampValid(svixTimestamp)) {
                log.warn("Invalid timestamp in webhook: {}", svixTimestamp);
                return false;
            }

            // Create the signed payload
            String signedPayload = svixId + "." + svixTimestamp + "." + payload;

            // Extract the webhook secret (remove the 'whsec_' prefix if present)
            String secret = webhookSecret.startsWith("whsec_") ?
                    webhookSecret.substring(6) : webhookSecret;

            // Decode the base64 secret
            byte[] secretBytes = Base64.getDecoder().decode(secret);

            // Create HMAC signature
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] signature = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

            // Encode signature to base64
            String expectedSignature = Base64.getEncoder().encodeToString(signature);

            // Extract signatures from the header (format: "v1,signature1 v1,signature2")
            String[] signatures = svixSignature.split(" ");

            for (String sig : signatures) {
                if (sig.startsWith("v1,")) {
                    String providedSignature = sig.substring(3);
                    if (constantTimeEquals(expectedSignature, providedSignature)) {
                        return true;
                    }
                }
            }

            log.warn("Webhook signature verification failed");
            return false;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying webhook signature: ", e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during webhook signature verification: ", e);
            return false;
        }
    }

    private boolean isTimestampValid(String timestampStr) {
        try {
            long timestamp = Long.parseLong(timestampStr);
            long currentTime = Instant.now().getEpochSecond();
            return Math.abs(currentTime - timestamp) <= TIMESTAMP_TOLERANCE;
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format: {}", timestampStr);
            return false;
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ actual.charAt(i);
        }
        return result == 0;
    }
}
