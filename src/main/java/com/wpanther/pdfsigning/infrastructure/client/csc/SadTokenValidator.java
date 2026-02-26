package com.wpanther.pdfsigning.infrastructure.client.csc;

import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Validates SAD (Signature Activation Data) tokens from CSC authorize responses.
 *
 * Security critical: Ensures the SAD token is valid before using it for signing operations.
 */
@Component
@Slf4j
public class SadTokenValidator {

    @Value("${app.csc.sad.min-expiry-seconds:60}")
    private long minExpirySeconds = 60;

    @Value("${app.csc.sad.max-expiry-seconds:3600}")
    private long maxExpirySeconds = 3600;

    /**
     * Validates a SAD token response from the CSC API.
     *
     * @param response The authorize response containing the SAD token
     * @param credentialId The credential ID that was requested
     * @throws SadTokenValidationException if validation fails
     */
    public void validate(CSCAuthorizeResponse response, String credentialId) {
        if (response == null) {
            throw new SadTokenValidationException("SAD token response is null");
        }

        // 1. Check SAD token is present
        String sad = response.getSAD();
        if (sad == null || sad.trim().isEmpty()) {
            throw new SadTokenValidationException("SAD token is null or empty");
        }

        // 2. Check SAD token format (should be a non-empty base64-like string)
        if (sad.length() < 20) {
            throw new SadTokenValidationException(
                "SAD token appears invalid (too short): " + sad.length() + " characters"
            );
        }

        // 3. Validate expiration if present
        Long expiresIn = response.getExpiresIn();
        if (expiresIn != null) {
            if (expiresIn <= 0) {
                throw new SadTokenValidationException(
                    "SAD token has already expired (expiresIn: " + expiresIn + ")"
                );
            }

            if (expiresIn < minExpirySeconds) {
                throw new SadTokenValidationException(
                    "SAD token expires too soon: " + expiresIn + " seconds (minimum: " + minExpirySeconds + ")"
                );
            }

            if (expiresIn > maxExpirySeconds) {
                log.warn("SAD token expiration exceeds maximum: {} seconds (max: {})",
                    expiresIn, maxExpirySeconds);
            }
        }

        log.debug("SAD token validated successfully for credential: {} (expiresIn: {})",
            credentialId, expiresIn);
    }

    /**
     * Checks if a SAD token is expired based on the issue time and expiresIn value.
     *
     * @param issuedAt When the SAD token was issued
     * @param expiresIn The expiresIn value from authorize response
     * @return true if expired, false otherwise
     */
    public boolean isExpired(Instant issuedAt, Long expiresIn) {
        if (issuedAt == null || expiresIn == null) {
            return false;
        }
        Instant expirationTime = issuedAt.plus(expiresIn, ChronoUnit.SECONDS);
        return Instant.now().isAfter(expirationTime);
    }

    /**
     * Gets the minimum allowed expiry time in seconds.
     */
    public long getMinExpirySeconds() {
        return minExpirySeconds;
    }

    /**
     * Gets the maximum allowed expiry time in seconds.
     */
    public long getMaxExpirySeconds() {
        return maxExpirySeconds;
    }

    /**
     * Exception thrown when SAD token validation fails.
     */
    public static class SadTokenValidationException extends RuntimeException {
        public SadTokenValidationException(String message) {
            super(message);
        }

        public SadTokenValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
