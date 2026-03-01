package com.wpanther.pdfsigning.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for CSC (Cloud Signature Consortium) API integration.
 * <p>
 * Groups all CSC-related configuration including authentication,
 * credential settings, hash algorithm, and SAD token validation.
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.csc")
public class CscProperties {

    /**
     * OAuth2 client ID for CSC API authentication.
     * May be null if the CSC service doesn't require explicit client ID.
     */
    private String clientId;

    /**
     * Credential ID to use for signing operations.
     * This identifies which key/certificate to use in the CSC service.
     */
    private String credentialId;

    /**
     * Hash algorithm to use for signature operations.
     * Default: SHA256
     */
    private String hashAlgo = "SHA256";

    /**
     * Certificate validation settings.
     */
    private final CertValidation certValidation = new CertValidation();

    /**
     * SAD (Signature Authorization Token) validation settings.
     */
    private final SadToken sadToken = new SadToken();

    /**
     * Certificate validation configuration.
     */
    @Data
    public static class CertValidation {

        /**
         * Whether certificate validation is enabled.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Maximum validity period for certificates in days.
         * Certificates valid beyond this period will be rejected.
         * Default: 365 days (1 year)
         */
        private int maxValidityDays = 365;

        /**
         * Minimum remaining validity period in days.
         * Certificates expiring sooner than this will trigger a warning.
         * Default: 7 days
         */
        private int minValidityRemainingDays = 7;
    }

    /**
     * SAD token validation configuration.
     */
    @Data
    public static class SadToken {

        /**
         * Minimum SAD token expiry time in seconds.
         * Tokens expiring sooner than this will be rejected.
         * Default: 60 seconds
         */
        private int minExpirySeconds = 60;

        /**
         * Maximum SAD token expiry time in seconds.
         * Tokens expiring later than this will be rejected.
         * Default: 3600 seconds (1 hour)
         */
        private int maxExpirySeconds = 3600;
    }
}
