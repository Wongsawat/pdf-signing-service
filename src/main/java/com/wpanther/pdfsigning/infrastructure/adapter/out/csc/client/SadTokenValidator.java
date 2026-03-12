package com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client;

import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.config.properties.CscProperties;
import lombok.extern.slf4j.Slf4j;
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

    private final CscProperties cscProperties;

    // Default values for testing
    private static final long DEFAULT_MIN_EXPIRY_SECONDS = 60;
    private static final long DEFAULT_MAX_EXPIRY_SECONDS = 3600;

    /**
     * Main constructor with dependency injection.
     */
    public SadTokenValidator(CscProperties cscProperties) {
        this.cscProperties = cscProperties;
    }

    /**
     * Default constructor for testing.
     */
    public SadTokenValidator() {
        this.cscProperties = null;
    }

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
            long minExpiry = cscProperties != null
                ? cscProperties.getSadToken().getMinExpirySeconds()
                : DEFAULT_MIN_EXPIRY_SECONDS;
            long maxExpiry = cscProperties != null
                ? cscProperties.getSadToken().getMaxExpirySeconds()
                : DEFAULT_MAX_EXPIRY_SECONDS;

            if (expiresIn <= 0) {
                throw new SadTokenValidationException(
                    "SAD token has already expired (expiresIn: " + expiresIn + ")"
                );
            }

            if (expiresIn < minExpiry) {
                throw new SadTokenValidationException(
                    "SAD token expires too soon: " + expiresIn + " seconds (minimum: " + minExpiry + ")"
                );
            }

            if (expiresIn > maxExpiry) {
                log.warn("SAD token expiration exceeds maximum: {} seconds (max: {})",
                    expiresIn, maxExpiry);
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
        return cscProperties != null
            ? cscProperties.getSadToken().getMinExpirySeconds()
            : DEFAULT_MIN_EXPIRY_SECONDS;
    }

    /**
     * Gets the maximum allowed expiry time in seconds.
     */
    public long getMaxExpirySeconds() {
        return cscProperties != null
            ? cscProperties.getSadToken().getMaxExpirySeconds()
            : DEFAULT_MAX_EXPIRY_SECONDS;
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
