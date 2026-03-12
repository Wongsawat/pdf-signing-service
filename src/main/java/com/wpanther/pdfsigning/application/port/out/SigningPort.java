package com.wpanther.pdfsigning.application.port.out;

import com.wpanther.pdfsigning.domain.model.PadesLevel;
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
     * Sign a PDF with PAdES signature and return certificate chain.
     * <p>
     * This method:
     * <ol>
     *   <li>Validates the certificate chain</li>
     *   <li>Signs the PDF using the configured PAdES level</li>
     *   <li>Returns both signed PDF and certificate chain for PEM encoding</li>
     * </ol>
     * </p>
     *
     * @param pdfBytes   Original PDF bytes
     * @param digest     Pre-computed SHA-256 digest of PDF byte range
     * @param padesLevel Desired PAdES conformance level
     * @return SigningResult containing signed PDF and certificate chain
     * @throws SigningException if signing fails
     */
    SigningResult signPdfWithCertChain(byte[] pdfBytes, byte[] digest, PadesLevel padesLevel) throws SigningException;

    /**
     * Validate certificate chain for signing purposes.
     *
     * @param certChain Certificate chain to validate
     * @throws SigningException if validation fails
     */
    void validateCertificateChain(X509Certificate[] certChain) throws SigningException;

    /**
     * Result of signing operation containing both signed PDF and certificate chain.
     */
    record SigningResult(
        byte[] signedPdf,
        X509Certificate[] certificateChain
    ) {}
}
