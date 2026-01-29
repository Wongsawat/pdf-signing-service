package com.invoice.pdfsigning.application.service;

import com.invoice.pdfsigning.domain.event.PdfGeneratedEvent;
import com.invoice.pdfsigning.domain.model.SignedPdfDocument;
import com.invoice.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.invoice.pdfsigning.domain.service.PdfSigningService;
import com.invoice.pdfsigning.infrastructure.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Application service that orchestrates the PDF signing workflow.
 *
 * This service:
 * 1. Checks for idempotency (already signed?)
 * 2. Creates or retrieves SignedPdfDocument aggregate
 * 3. Validates retry limits
 * 4. Delegates to PdfSigningService for actual signing
 * 5. Updates document state
 * 6. Publishes PdfSignedEvent to Kafka
 * 7. Handles errors and retry logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfSigningOrchestrationService {

    private final SignedPdfDocumentRepository repository;
    private final PdfSigningService pdfSigningService;
    private final EventPublisher eventPublisher;

    @Value("${app.signing.max-retries:3}")
    private int maxRetries;

    /**
     * Processes a PDF signing request from a PdfGeneratedEvent.
     *
     * @param event the PDF generated event
     */
    @Transactional
    public void processSigningRequest(PdfGeneratedEvent event) {
        log.info("Processing PDF signing request for invoice: {}", event.getInvoiceId());

        try {
            // 1. Check idempotency - have we already signed this invoice?
            Optional<SignedPdfDocument> existing = repository.findByInvoiceId(event.getInvoiceId());

            SignedPdfDocument document;
            if (existing.isPresent()) {
                document = existing.get();
                log.info("Found existing signed PDF document for invoice: {}, status: {}",
                        event.getInvoiceId(), document.getStatus());

                // If already completed, skip
                if (document.isCompleted()) {
                    log.info("PDF already signed for invoice: {}, skipping", event.getInvoiceId());
                    return;
                }

                // If failed, check retry limits
                if (document.isFailed()) {
                    if (!document.canRetry(maxRetries)) {
                        log.error("Max retries ({}) exceeded for invoice: {}", maxRetries, event.getInvoiceId());
                        return;
                    }
                    log.info("Retrying PDF signing for invoice: {}, retry count: {}",
                            event.getInvoiceId(), document.getRetryCount());
                    document.incrementRetryCount();
                    document.resetForRetry();
                    repository.save(document);
                }
            } else {
                // 2. Create new SignedPdfDocument aggregate
                log.info("Creating new SignedPdfDocument for invoice: {}", event.getInvoiceId());
                document = SignedPdfDocument.create(
                        event.getInvoiceId(),
                        event.getInvoiceNumber(),
                        event.getDocumentUrl(),
                        event.getFileSize(),
                        event.getCorrelationId(),
                        event.getDocumentType()
                );
                document = repository.save(document);
                log.info("Created SignedPdfDocument with ID: {}", document.getId().asString());
            }

            // 3. Start signing process
            document.startSigning();
            document = repository.save(document);
            log.info("Started signing process for invoice: {}", event.getInvoiceId());

            // 4. Sign the PDF via domain service
            try {
                PdfSigningService.SignedPdfResult result = pdfSigningService.signPdf(
                        document.getOriginalPdfUrl(),
                        document.getId().asString()
                );

                // 5. Mark as completed
                document.markCompleted(
                        result.getSignedPdfPath(),
                        result.getSignedPdfUrl(),
                        result.getSignedPdfSize(),
                        result.getTransactionId(),
                        result.getCertificate(),
                        result.getSignatureLevel(),
                        result.getSignatureTimestamp()
                );
                document = repository.save(document);
                log.info("Successfully signed PDF for invoice: {}", event.getInvoiceId());

                // 6. Publish PdfSignedEvent
                eventPublisher.publishPdfSigned(document, event.getCorrelationId());
                log.info("Published PdfSignedEvent for invoice: {}", event.getInvoiceId());

            } catch (PdfSigningService.PdfSigningException e) {
                // Signing failed - mark as failed
                log.error("PDF signing failed for invoice: {}", event.getInvoiceId(), e);
                document.markFailed(e.getMessage());
                repository.save(document);
                throw e;
            }

        } catch (Exception e) {
            log.error("Unexpected error processing PDF signing for invoice: {}",
                    event.getInvoiceId(), e);
            throw new RuntimeException("Failed to process PDF signing request", e);
        }
    }
}
