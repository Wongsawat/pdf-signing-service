package com.wpanther.pdfsigning.application.service;

import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.domain.service.PdfSigningService;
import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Handles saga commands from the orchestrator.
 * Publishes BOTH saga reply (to orchestrator) AND notification event (to notification-service).
 * Follows xml-signing-service pattern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final SignedPdfDocumentRepository documentRepository;
    private final PdfSigningService signingService;
    private final PdfSigningEventPublisher eventPublisher;  // Combined publisher

    @Value("${app.signing.max-retries:3}")
    private int maxRetries;

    /**
     * Handles ProcessPdfSigningCommand from saga orchestrator.
     * Signs the PDF and sends SUCCESS/FAILURE reply + notification.
     */
    @Transactional
    public void handleProcessCommand(ProcessPdfSigningCommand command) {
        log.info("Processing PDF signing command: sagaId={}, documentId={}, documentType={}",
            command.getSagaId(), command.getDocumentId(), command.getDocumentType());

        try {
            // 1. Check idempotency - use documentId (from command)
            Optional<SignedPdfDocument> existing = documentRepository.findByInvoiceId(command.getDocumentId());

            // 2. Check if already completed (idempotent)
            if (existing.isPresent() && existing.get().isCompleted()) {
                log.info("PDF already signed for documentId={}, sending SUCCESS reply", command.getDocumentId());
                sendSuccessReply(command, existing.get());
                return;
            }

            // 3. Check retry limits
            if (existing.isPresent() && existing.get().getRetryCount() >= maxRetries) {
                log.warn("Max retries exceeded for documentId={}, sending FAILURE reply", command.getDocumentId());
                eventPublisher.publishFailure(
                    command.getSagaId(),
                    command.getSagaStep(),
                    command.getCorrelationId(),
                    command.getDocumentId(),
                    command.getInvoiceNumber(),
                    command.getDocumentType(),
                    "Maximum retry attempts exceeded for PDF signing"
                );
                return;
            }

            // 4. Create or retrieve aggregate
            SignedPdfDocument document = existing.orElseGet(() ->
                SignedPdfDocument.create(
                    command.getDocumentId(),
                    command.getInvoiceNumber(),
                    command.getPdfUrl(),
                    command.getPdfSize(),
                    command.getCorrelationId(),
                    command.getDocumentType()
                )
            );

            // 5. Start signing (state transition)
            document.startSigning();
            documentRepository.save(document);

            // 6. Execute signing
            PdfSigningService.SignedPdfResult result = signingService.signPdf(
                command.getPdfUrl(),
                document.getId().toString()
            );

            // 7. Mark completed (state transition)
            document.markCompleted(
                result.getSignedPdfPath(),
                result.getSignedPdfUrl(),
                result.getSignedPdfSize(),
                result.getTransactionId(),
                result.getCertificate(),
                result.getSignatureLevel(),
                result.getSignatureTimestamp()
            );
            documentRepository.save(document);

            // 8. Send SUCCESS reply AND notification event (dual publishing)
            eventPublisher.publishSuccess(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                command.getDocumentId(),           // invoiceId
                command.getInvoiceNumber(),
                command.getDocumentType(),
                document.getId().toString(),
                document.getSignedPdfUrl(),
                document.getSignedPdfSize(),
                document.getTransactionId(),
                document.getCertificate(),
                document.getSignatureLevel(),
                result.getSignatureTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
            );

            log.info("PDF signing completed successfully for documentId={}, sagaId={}",
                command.getDocumentId(), command.getSagaId());

        } catch (Exception e) {
            // 9. Handle failure
            log.error("PDF signing failed for documentId={}, sagaId={}",
                command.getDocumentId(), command.getSagaId(), e);

            // Get the document to mark as failed
            documentRepository.findByInvoiceId(command.getDocumentId()).ifPresent(document -> {
                document.markFailed(e.getMessage());
                document.incrementRetryCount();
                documentRepository.save(document);
            });

            // Send FAILURE reply AND notification event
            eventPublisher.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                command.getDocumentId(),
                command.getInvoiceNumber(),
                command.getDocumentType(),
                e.getMessage()
            );
        }
    }

    /**
     * Handles CompensatePdfSigningCommand from saga orchestrator.
     * Deletes the signed document and sends COMPENSATED reply.
     */
    @Transactional
    public void handleCompensation(CompensatePdfSigningCommand command) {
        log.info("Compensating PDF signing: sagaId={}, documentId={}",
            command.getSagaId(), command.getDocumentId());

        try {
            // 1. Find and delete the signed document
            Optional<SignedPdfDocument> existing = documentRepository.findByInvoiceId(command.getDocumentId());

            if (existing.isPresent()) {
                SignedPdfDocument document = existing.get();

                // Delete signed PDF file from filesystem
                try {
                    if (document.getSignedPdfPath() != null) {
                        Path filePath = Path.of(document.getSignedPdfPath());
                        if (Files.exists(filePath)) {
                            Files.delete(filePath);
                            log.info("Deleted signed PDF file: {}", document.getSignedPdfPath());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete signed PDF file: {}", document.getSignedPdfPath(), e);
                }

                // Delete from database
                documentRepository.deleteById(document.getId());
                log.info("Deleted SignedPdfDocument {} for compensation", document.getId());
            } else {
                log.info("No signed document found for documentId={}, compensation already done or never existed",
                    command.getDocumentId());
            }

            // 2. Send COMPENSATED reply (idempotent)
            // Note: No notification event for compensation - orchestrator handles that
            eventPublisher.publishCompensated(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );

            log.info("Compensation completed for sagaId={}, documentId={}",
                command.getSagaId(), command.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to compensate PDF signing for sagaId={}, documentId={}",
                command.getSagaId(), command.getDocumentId(), e);

            // Send FAILURE reply if compensation fails
            eventPublisher.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                command.getDocumentId(),
                "",
                command.getDocumentType(),
                "Compensation failed: " + e.getMessage()
            );
        }
    }

    /**
     * Sends SUCCESS reply for an already completed document (idempotent).
     */
    private void sendSuccessReply(ProcessPdfSigningCommand command, SignedPdfDocument document) {
        eventPublisher.publishSuccess(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId(),
            command.getDocumentId(),
            command.getInvoiceNumber(),
            command.getDocumentType(),
            document.getId().toString(),
            document.getSignedPdfUrl(),
            document.getSignedPdfSize(),
            document.getTransactionId(),
            document.getCertificate(),
            document.getSignatureLevel(),
            document.getSignatureTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
        );
    }
}
