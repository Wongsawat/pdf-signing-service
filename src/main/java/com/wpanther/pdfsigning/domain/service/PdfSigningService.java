package com.wpanther.pdfsigning.domain.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Domain service interface for PDF signing operations.
 *
 * This interface defines the contract for signing PDFs using PAdES
 * (PDF Advanced Electronic Signatures) format via the CSC API v2.0.
 *
 * Implementation is in the infrastructure layer.
 */
public interface PdfSigningService {

    /**
     * Signs a PDF document using PAdES-BASELINE-T format.
     *
     * @param pdfUrl URL to download the unsigned PDF
     * @param documentId unique identifier for this signing operation
     * @return SignedPdfResult containing the signed PDF details
     * @throws PdfSigningException if signing fails
     */
    SignedPdfResult signPdf(String pdfUrl, String documentId);

    /**
     * Result of a PDF signing operation.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class SignedPdfResult {
        /**
         * Filesystem path where the signed PDF is stored
         */
        private String signedPdfPath;

        /**
         * Public URL to access the signed PDF
         */
        private String signedPdfUrl;

        /**
         * Size of the signed PDF in bytes
         */
        private Long signedPdfSize;

        /**
         * CSC API transaction ID
         */
        private String transactionId;

        /**
         * PEM-encoded signing certificate
         */
        private String certificate;

        /**
         * Signature level (e.g., PAdES-BASELINE-T)
         */
        private String signatureLevel;

        /**
         * Timestamp from the signing service
         */
        private LocalDateTime signatureTimestamp;
    }

    /**
     * Exception thrown when PDF signing fails.
     */
    class PdfSigningException extends RuntimeException {
        public PdfSigningException(String message) {
            super(message);
        }

        public PdfSigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
