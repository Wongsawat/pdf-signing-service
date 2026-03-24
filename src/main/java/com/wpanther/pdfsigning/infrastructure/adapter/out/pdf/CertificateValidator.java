package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import com.wpanther.pdfsigning.infrastructure.config.properties.CscProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.cert.CertificateException;
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
 * Validation stages:
 * <ol>
 *   <li>End-entity validity period, key usage, and extended key usage</li>
 *   <li>Chain structure — each cert is signed by the next (signature verification)</li>
 *   <li>PKIX path validation — chain terminates at a root CA trusted by the JVM
 *       default trust store (cacerts). This catches fraudulent chains whose root
 *       is not a known trusted CA.</li>
 * </ol>
 *
 * PKIX path validation only runs when this class is constructed with
 * {@link CscProperties} (production). The no-arg constructor (test mode) skips PKIX
 * to avoid depending on the JVM's cacerts store with mock certificates.
 */
@Component
@Slf4j
public class CertificateValidator {

    private final CscProperties cscProperties;

    // Test-only field
    private boolean testValidationEnabled = false;

    // PKIX validation infrastructure (lazily initialized)
    private TrustManagerFactory tmf;

    /**
     * Main constructor with dependency injection.
     */
    public CertificateValidator(CscProperties cscProperties) {
        this.cscProperties = cscProperties;
    }

    /**
     * Default constructor for testing.
     * Package-private to prevent accidental use in production.
     */
    CertificateValidator() {
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
     * Performs three stages of validation:
     * <ol>
     *   <li>End-entity validity period, key usage, and extended key usage</li>
     *   <li>Chain structure verification (each cert issued by the next)</li>
     *   <li>PKIX path validation — chain terminates at a root CA trusted by the JVM
     *       default trust store (cacerts). Skipped when using the no-arg constructor.</li>
     * </ol>
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

            // 3. PKIX path validation — verifies chain anchors to a trusted root CA
            //    in the JVM default trust store (cacerts). This catches fraudulent
            //    chains where a self-signed root is not a known trusted CA.
            if (cscProperties != null) {
                // PKIX validation only runs in production mode (with CscProperties injected).
                // In test mode (no-arg constructor) we skip PKIX to avoid depending on
                // the JVM's cacerts store with mock certificates.
                validatePkixPath(certChain);
            }

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
     * Initialises the PKIX TrustManagerFactory lazily.
     * Uses the JVM default KeyStore (cacerts) as the trust store.
     */
    private synchronized void initTmf() {
        if (tmf != null) {
            return;
        }
        try {
            tmf = TrustManagerFactory.getInstance("PKIX");
            // Initialise with null to use the system default KeyStore (cacerts),
            // which contains trusted root CA certificates.
            tmf.init((KeyStore) null);
            log.debug("PKIX TrustManagerFactory initialised with system default trust store");
        } catch (Exception e) {
            log.warn("Failed to initialise PKIX TrustManagerFactory: {}", e.getMessage());
        }
    }

    /**
     * Performs PKIX certification path validation.
     * <p>
     * Uses the JDK's built-in PKIX TrustManagerFactory to validate that the
     * certificate chain terminates at a root CA trusted by the JVM's default
     * trust store (cacerts). This catches fraudulent chains where a self-signed
     * root is not a known trusted CA — structural chain validation alone would
     * pass such chains because it only checks signatures, not trust anchors.
     * </p>
     *
     * @param certChain the certificate chain to validate (end-entity first)
     * @throws CertificateValidationException if PKIX validation fails
     */
    private void validatePkixPath(X509Certificate[] certChain) {
        initTmf();
        if (tmf == null) {
            log.warn("PKIX TrustManagerFactory not available — skipping PKIX path validation");
            return;
        }
        try {
            javax.net.ssl.X509TrustManager x509Tm = null;
            for (javax.net.ssl.TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof javax.net.ssl.X509TrustManager) {
                    x509Tm = (javax.net.ssl.X509TrustManager) tm;
                    break;
                }
            }
            if (x509Tm == null) {
                log.warn("No X509TrustManager available in PKIX TrustManagerFactory — skipping");
                return;
            }
            // checkClientTrusted performs full PKIX path validation including
            // checking that the chain terminates at a trusted root CA.
            x509Tm.checkClientTrusted(certChain, "Generic");
            log.debug("PKIX path validation successful — chain terminates at trusted root CA");
        } catch (CertificateException e) {
            throw new CertificateValidationException(
                "PKIX certificate validation failed: no trusted certificate path found. " +
                "The certificate chain may not be issued by a trusted root CA. " +
                "Verify that the Thai e-Tax root CA is in the JVM trust store.", e);
        } catch (Exception e) {
            log.warn("PKIX path validation could not be performed: {}", e.getMessage());
        }
    }

    public static class CertificateValidationException extends RuntimeException {
        public CertificateValidationException(String message) {
            super(message);
        }

        public CertificateValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
