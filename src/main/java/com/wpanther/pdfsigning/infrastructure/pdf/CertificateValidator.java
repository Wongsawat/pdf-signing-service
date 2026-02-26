package com.wpanther.pdfsigning.infrastructure.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Validates X.509 certificates from CSC API responses.
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

    @Value("${app.csc.cert.validation.enabled:true}")
    private boolean validationEnabled;

    @Value("${app.csc.cert.max-validity-days:365}")
    private long maxValidityDays = 365;

    @Value("${app.csc.cert.min-validity-remaining-days:7}")
    private long minValidityRemainingDays = 7;

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

        if (daysRemaining < minValidityRemainingDays) {
            log.warn("Certificate expires soon: {} days remaining (minimum: {})",
                daysRemaining, minValidityRemainingDays);
        }

        // 3. Check maximum validity period (from issuance)
        long certValidityDays = ChronoUnit.DAYS.between(
            notBefore.toInstant(),
            notAfter.toInstant()
        );

        if (certValidityDays > maxValidityDays) {
            log.warn("Certificate validity period exceeds maximum: {} days (max: {})",
                certValidityDays, maxValidityDays);
        }

        // 4. Check key usage for digital signatures
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null) {
            // KeyUsage bit 0 = digitalSignature
            if (!keyUsage[0]) {
                log.warn("Certificate keyUsage does not include digitalSignature");
            }
            // KeyUsage bit 1 = nonRepudiation
            if (!keyUsage[1]) {
                log.warn("Certificate keyUsage does not include nonRepudiation");
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
     * Sets whether certificate validation is enabled.
     */
    public void setValidationEnabled(boolean enabled) {
        this.validationEnabled = enabled;
        log.info("Certificate validation {}", enabled ? "enabled" : "disabled");
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
