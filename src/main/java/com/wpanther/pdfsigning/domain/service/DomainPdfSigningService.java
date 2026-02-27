package com.wpanther.pdfsigning.domain.service;

import com.wpanther.pdfsigning.domain.model.*;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.domain.port.DocumentDownloadPort;
import com.wpanther.pdfsigning.domain.port.DocumentStoragePort;
import com.wpanther.pdfsigning.domain.port.PdfGenerationPort;
import com.wpanther.pdfsigning.domain.port.SigningPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core domain service for PDF signing operations.
 * <p>
 * This service contains the business logic for PDF signing,
 * depending only on port interfaces (no infrastructure dependencies).
 * </p>
 * <p>
 * Dependencies are injected via constructor, enabling easy testing
 * with mock implementations of the ports.
 * </p>
 */
public class DomainPdfSigningService {

    private static final Logger log = LoggerFactory.getLogger(DomainPdfSigningService.class);

    private final SigningPort signingPort;
    private final PdfGenerationPort pdfPort;
    private final DocumentStoragePort storagePort;
    private final DocumentDownloadPort downloadPort;
    private final SignedPdfDocumentRepository repository;

    /**
     * Creates a new domain PDF signing service.
     *
     * @param signingPort   Port for signing operations (CSC, mock, etc.)
     * @param pdfPort       Port for PDF-specific processing (digest, embedding)
     * @param storagePort   Port for document storage
     * @param downloadPort  Port for downloading PDF documents
     * @param repository    Repository for persisting signed documents
     */
    public DomainPdfSigningService(SigningPort signingPort,
                                    PdfGenerationPort pdfPort,
                                    DocumentStoragePort storagePort,
                                    DocumentDownloadPort downloadPort,
                                    SignedPdfDocumentRepository repository) {
        this.signingPort = signingPort;
        this.pdfPort = pdfPort;
        this.storagePort = storagePort;
        this.downloadPort = downloadPort;
        this.repository = repository;
    }

    /**
     * Sign a PDF document with PAdES signature.
     * <p>
     * This method orchestrates the signing workflow:
     * <ol>
     *   <li>Validates the certificate chain</li>
     *   <li>Computes the byte range digest</li>
     *   <li>Signs the digest</li>
     *   <li>Embeds the signature into the PDF</li>
     *   <li>Stores the signed PDF</li>
     *   <li>Persists the document aggregate</li>
     * </ol>
     * </p>
     *
     * @param invoiceId       Business identifier (for idempotency)
     * @param invoiceNumber   Human-readable invoice number
     * @param originalPdfUrl  URL of the unsigned PDF
     * @param originalPdfSize Size of the unsigned PDF in bytes
     * @param certChain       Certificate chain for signature
     * @param padesLevel      Desired PAdES conformance level
     * @param correlationId   Correlation ID for tracing
     * @return The signed and stored document aggregate
     * @throws SigningException if signing fails
     * @throws StorageException if storage fails
     */
    public SignedPdfDocument signPdf(String invoiceId,
                                     String invoiceNumber,
                                     String originalPdfUrl,
                                     Long originalPdfSize,
                                     X509Certificate[] certChain,
                                     PadesLevel padesLevel,
                                     String correlationId) {
        log.info("Starting PDF signing for invoice: {}, correlation: {}", invoiceId, correlationId);

        // Step 1: Validate certificate chain
        log.debug("Validating certificate chain with {} certificates", certChain.length);
        signingPort.validateCertificateChain(certChain);

        // Step 2: Create the aggregate in PENDING state
        SignedPdfDocument document = SignedPdfDocument.create(
            invoiceId,
            invoiceNumber,
            originalPdfUrl,
            originalPdfSize,
            correlationId,
            "TAX_INVOICE"
        );
        document.startSigning();
        document = repository.save(document);

        // Step 3: Download PDF
        log.debug("Downloading PDF from URL: {}", originalPdfUrl);
        byte[] pdfBytes = downloadPort.downloadPdf(originalPdfUrl);

        // Step 4: Compute byte range digest
        log.debug("Computing PDF byte range digest");
        byte[] digest = pdfPort.computeByteRangeDigest(pdfBytes);
        log.debug("Computed digest: {} bytes", digest.length);

        // Step 5: Sign the digest
        log.debug("Signing digest with certificate chain");
        byte[] signedPdfBytes = signingPort.signPdf(pdfBytes, digest, certChain);
        log.debug("Signed PDF: {} bytes", signedPdfBytes.length);

        // Step 6: Store the signed PDF
        log.debug("Storing signed PDF");
        String storageUrl = storagePort.store(signedPdfBytes, "SIGNED_PDF", document);
        String storagePath = extractPathFromUrl(storageUrl);
        log.debug("Stored signed PDF at: {}", storageUrl);

        // Step 7: Mark as completed
        String transactionId = UUID.randomUUID().toString();
        String certificate = extractCertificatePem(certChain);
        document.markCompleted(
            storagePath,
            storageUrl,
            (long) signedPdfBytes.length,
            transactionId,
            certificate,
            padesLevel.getCode(),
            LocalDateTime.now()
        );
        SignedPdfDocument saved = repository.save(document);

        log.info("PDF signing completed for invoice: {}", invoiceId);
        return saved;
    }

    /**
     * Compensate/rollback a signing operation by deleting the signed PDF.
     *
     * @param documentId ID of the document to compensate
     * @throws SigningException if document not found
     */
    public void compensateSigning(SignedPdfDocumentId documentId) {
        log.info("Compensating signing for document: {}", documentId);

        SignedPdfDocument document = repository.findById(documentId)
            .orElseThrow(() -> new SigningException("Document not found: " + documentId));

        if (document.getSignedPdfUrl() != null) {
            storagePort.delete(document.getSignedPdfUrl());
        }

        repository.deleteById(document.getId());
        log.info("Signing compensation completed for document: {}", documentId);
    }

    /**
     * Extract storage path from storage URL.
     */
    private String extractPathFromUrl(String storageUrl) {
        // Simple implementation - could be enhanced based on URL format
        return storageUrl.substring(storageUrl.lastIndexOf('/') + 1);
    }

    /**
     * Extract PEM-encoded certificate from certificate chain.
     */
    private String extractCertificatePem(X509Certificate[] certChain) {
        // This would convert X509Certificate to PEM format
        // For now, return a placeholder
        return "-----BEGIN CERTIFICATE-----\nPLACEHOLDER\n-----END CERTIFICATE-----";
    }
}
