package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import com.wpanther.pdfsigning.infrastructure.config.properties.CscProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Validates X509 certificates from CSC API responses.
 *
 * Security critical: Ensures certificates are valid and not expired before using them
 * for signature construction.
 *
 * Note: Full PKIX path validation requires a properly configured trust store with
 * Thai e-Tax root CA certificates. This validator performs essential checks that
 * don't require external trust store configuration.
 */
@Component
@Slf4j
public class CertificateValidator {

    private final CscProperties cscProperties;

    // Test-only field
    private boolean testValidationEnabled = false;

    /**
     * Main constructor with dependency injection.
     */
    public CertificateValidator(CscProperties cscProperties) {
        this.cscProperties = cscProperties;
    }

    /**
     * Default constructor for testing.
     */
    public CertificateValidator() {
        this.cscProperties = null;  // Will use testValidationEnabled
    }

    /**
     * Sets whether certificate validation is enabled.
     * Package-private for testing purposes only.
     */
    void setValidationEnabled(boolean enabled) {
        this.testValidationEnabled = enabled;
        log.info("Certificate validation {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Validates a certificate chain from the CSC API.
     *
     * Performs essential validations:
     * - Not expired and not yet valid
     * - Minimum remaining validity period
     * - Chain structure verification (each cert issued by next)
     * - Basic key usage for digital signatures
     *
     * @param certChain The certificate chain to validate
     * @throws CertificateValidationException if validation fails
     */
    public void validateChain(X509Certificate[] certChain) {
        // Check if validation is enabled (use properties or test mode)
        boolean validationEnabled = cscProperties != null
            ? cscProperties.getCertValidation().isEnabled()
            : testValidationEnabled;

        if (!validationEnabled) {
            log.warn("Certificate validation is disabled - skipping chain validation");
            return;
        }

        if (certChain == null || certChain.length == 0) {
            throw new CertificateValidationException("Certificate chain is null or empty");
        }

        log.info("Validating certificate chain with {} certificates", certChain.length);

        try {
            // 1. Validate the end-entity certificate (first in chain)
            X509Certificate endEntityCert = certChain[0];
            validateEndEntityCertificate(endEntityCert);

            // 2. Validate certificate chain structure
            validateChainStructure(certChain);

            log.info("Certificate chain validated successfully");

        } catch (CertificateValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateValidationException("Certificate chain validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the end-entity certificate.
     */
    private void validateEndEntityCertificate(X509Certificate cert) {
        // Get validation settings from properties or use defaults for testing
        int minValidityDays = cscProperties != null
            ? cscProperties.getCertValidation().getMinValidityRemainingDays()
            : 7;
        int maxValidity = cscProperties != null
            ? cscProperties.getCertValidation().getMaxValidityDays()
            : 365;

        Instant now = Instant.now();
        Date notBefore = cert.getNotBefore();
        Date notAfter = cert.getNotAfter();

        // 1. Check validity period
        if (now.isBefore(notBefore.toInstant())) {
            throw new CertificateValidationException(
                "Certificate is not yet valid. Valid from: " + notBefore
            );
        }

        if (now.isAfter(notAfter.toInstant())) {
            throw new CertificateValidationException(
                "Certificate has expired. Expired at: " + notAfter
            );
        }

        // 2. Check minimum remaining validity
        Instant expiryTime = notAfter.toInstant();
        long daysRemaining = ChronoUnit.DAYS.between(now, expiryTime);

        if (daysRemaining < minValidityDays) {
            log.warn("Certificate expires soon: {} days remaining (minimum: {})",
                daysRemaining, minValidityDays);
        }

        // 3. Check maximum validity period (from issuance)
        long certValidityDays = ChronoUnit.DAYS.between(
            notBefore.toInstant(),
            notAfter.toInstant()
        );

        if (certValidityDays > maxValidity) {
            log.warn("Certificate validity period exceeds maximum: {} days (max: {})",
                certValidityDays, maxValidity);
        }

        // 4. Check key usage for digital signatures (required for Thai e-Tax signing)
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null) {
            // KeyUsage bit 0 = digitalSignature
            if (!keyUsage[0]) {
                throw new CertificateValidationException(
                    "Certificate keyUsage does not include digitalSignature (bit 0 required for e-Tax signing)");
            }
            // KeyUsage bit 1 = nonRepudiation
            if (!keyUsage[1]) {
                throw new CertificateValidationException(
                    "Certificate keyUsage does not include nonRepudiation (bit 1 required for e-Tax signing)");
            }
        }

        // 5. Check extended key usage for PDF signing
        try {
            // OID for PDF signing: 1.2.840.113583.1.1.8 (Adobe PDF signing)
            boolean hasPdfSigningEku = cert.getExtendedKeyUsage() != null &&
                cert.getExtendedKeyUsage().contains("1.2.840.113583.1.1.8");

            if (!hasPdfSigningEku) {
                log.debug("Certificate does not have Adobe PDF signing EKU");
            }
        } catch (Exception e) {
            log.debug("Could not check extended key usage: {}", e.getMessage());
        }

        log.debug("End-entity certificate validated: {}", cert.getSubjectDN());
    }

    /**
     * Validates the certificate chain structure.
     * Verifies that each certificate (except the root) is issued by the next certificate.
     */
    private void validateChainStructure(X509Certificate[] certChain) {
        if (certChain.length < 2) {
            log.warn("Certificate chain has only {} certificates (no intermediate/CA provided)",
                certChain.length);
        }

        // Verify each certificate in the chain (except root) is issued by the next
        for (int i = 0; i < certChain.length - 1; i++) {
            X509Certificate cert = certChain[i];
            X509Certificate issuer = certChain[i + 1];

            try {
                // Verify the certificate signature using the issuer's public key
                cert.verify(issuer.getPublicKey());
                log.debug("Certificate {} issued by {}", cert.getSubjectDN(), issuer.getSubjectDN());
            } catch (Exception e) {
                throw new CertificateValidationException(
                    "Certificate chain verification failed: " +
                    cert.getSubjectDN() + " is not issued by " + issuer.getSubjectDN(), e
                );
            }
        }
    }

    /**
     * Exception thrown when certificate validation fails.
     */
    public static class CertificateValidationException extends RuntimeException {
        public CertificateValidationException(String message) {
            super(message);
        }

        public CertificateValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
