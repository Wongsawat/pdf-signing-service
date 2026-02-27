package com.wpanther.pdfsigning.domain.port;

import com.wpanther.pdfsigning.domain.model.SigningException;

import java.security.cert.X509Certificate;

/**
 * Port for PDF signing operations.
 * <p>
 * The domain depends on this interface - infrastructure provides implementations
 * such as CSC-based signing or mock signing for testing.
 * </p>
 */
public interface SigningPort {

    /**
     * Sign a PDF with PAdES-BASELINE-T signature.
     *
     * @param pdfBytes  Original PDF bytes
     * @param digest    Pre-computed SHA-256 digest of PDF byte range
     * @param certChain Certificate chain for signature
     * @return Signed PDF bytes
     * @throws SigningException if signing fails
     */
    byte[] signPdf(byte[] pdfBytes, byte[] digest, X509Certificate[] certChain) throws SigningException;

    /**
     * Validate certificate chain for signing purposes.
     *
     * @param certChain Certificate chain to validate
     * @throws SigningException if validation fails
     */
    void validateCertificateChain(X509Certificate[] certChain) throws SigningException;
}
