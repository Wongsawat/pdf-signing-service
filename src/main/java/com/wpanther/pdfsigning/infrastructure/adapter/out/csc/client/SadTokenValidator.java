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
    // Clock skew tolerance: accounts for local clock being behind CSC service clock.
    // If the remaining validity (expiresIn) is <= this tolerance, the token is
    // considered expired to avoid signHash failing due to CSC-side expiration.
    private static final long DEFAULT_CLOCK_SKEW_TOLERANCE_SECONDS = 60;

    /**
     * Main constructor with dependency injection.
     */
    public SadTokenValidator(CscProperties cscProperties) {
        this.cscProperties = cscProperties;
    }

    /**
     * Package-private constructor for testing — use {@link #SadTokenValidator(CscProperties)}
     * in production to ensure configuration is always injected.
     */
    SadTokenValidator() {
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
     * <p>Applies a clock skew tolerance: if the remaining validity window is less than
     * or equal to the tolerance, the token is considered expired. This prevents
     * signHash from failing with "token expired" if the local clock is behind the
     * CSC service clock (CSC will reject an expired token anyway).</p>
     *
     * @param issuedAt When the SAD token was issued (local clock at authorization time)
     * @param expiresIn The expiresIn value from authorize response (CSC-side validity)
     * @return true if expired or within clock skew tolerance window, false otherwise
     */
    public boolean isExpired(Instant issuedAt, Long expiresIn) {
        if (issuedAt == null || expiresIn == null) {
            return false;
        }
        long clockSkewTolerance = cscProperties != null
            ? cscProperties.getSadToken().getClockSkewToleranceSeconds()
            : DEFAULT_CLOCK_SKEW_TOLERANCE_SECONDS;

        // Remaining time according to local clock: issuedAt + expiresIn - now
        // If remaining <= clockSkewTolerance, we may already be expired on CSC side.
        Instant expirationTime = issuedAt.plus(expiresIn, ChronoUnit.SECONDS);
        Instant nowWithTolerance = Instant.now().plus(clockSkewTolerance, ChronoUnit.SECONDS);
        return nowWithTolerance.isAfter(expirationTime);
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
