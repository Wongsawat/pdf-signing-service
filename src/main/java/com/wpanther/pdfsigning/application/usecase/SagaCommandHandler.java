package com.wpanther.pdfsigning.application.usecase;

import com.wpanther.pdfsigning.application.dto.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.application.dto.event.ProcessPdfSigningCommand;
import com.wpanther.pdfsigning.application.port.out.PdfSignedEventPort;
import com.wpanther.pdfsigning.application.port.out.PdfSagaReplyPort;
import com.wpanther.pdfsigning.domain.model.*;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.infrastructure.config.properties.SigningProperties;
import com.wpanther.pdfsigning.infrastructure.config.properties.PadesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Application service that implements {@link SagaCommandPort}.
 * <p>
 * This is the primary entry point from infrastructure (Kafka) into the application.
 * It orchestrates the PDF signing workflow using hexagonal architecture:
 * <ul>
 *   <li>Domain service ({@link DomainPdfSigningService}) for business logic</li>
 *   <li>Event publishing for saga replies and notifications</li>
 * </ul>
 * </p>
 * <p>
 * Implements hexagonal architecture pattern - this class implements
 * a port interface ({@link SagaCommandPort}) that is called by
 * primary adapters (Kafka consumers).
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler implements SagaCommandPort {

    private final SignedPdfDocumentRepository documentRepository;
    private final DomainPdfSigningService domainPdfSigningService;
    private final PdfSagaReplyPort sagaReplyPort;
    private final PdfSignedEventPort pdfSignedEventPort;
    private final SigningProperties signingProperties;
    private final PadesProperties padesProperties;

    /**
     * Handles ProcessPdfSigningCommand from saga orchestrator (SagaCommandPort implementation).
     * Delegates to {@link #handleProcessCommand(ProcessPdfSigningCommand)}.
     */
    @Override
    public void handleProcessPdfSigning(ProcessPdfSigningCommand command) {
        handleProcessCommand(command);
    }

    /**
     * Handles CompensatePdfSigningCommand from saga orchestrator (SagaCommandPort implementation).
     * Delegates to {@link #handleCompensation(CompensatePdfSigningCommand)}.
     */
    @Override
    public void handleCompensatePdfSigning(CompensatePdfSigningCommand command) {
        handleCompensation(command);
    }

    /**
     * Handles ProcessPdfSigningCommand from saga orchestrator.
     * Signs the PDF and sends SUCCESS/FAILURE reply + notification.
     */
    @Transactional
    public void handleProcessCommand(ProcessPdfSigningCommand command) {
        log.info("Processing PDF signing command: sagaId={}, documentId={}, documentType={}",
            command.getSagaId(), command.getDocumentId(), command.getDocumentType());

        // 1. Check idempotency - use documentId (from command)
        Optional<SignedPdfDocument> existing = documentRepository.findByInvoiceId(command.getDocumentId());

        // 2. Already completed — idempotent reply, then return
        if (existing.isPresent() && existing.get().isCompleted()) {
            log.info("PDF already signed for documentId={}, sending SUCCESS reply", command.getDocumentId());
            sendSuccessReply(command, existing.get());
            return;
        }

        // 3. Max retries exceeded — publish failure and return (OUTSIDE the signing try/catch)
        if (existing.isPresent() && existing.get().getRetryCount() >= signingProperties.getMaxRetries()) {
            log.warn("Max retries exceeded for documentId={}, sending FAILURE reply", command.getDocumentId());
            sagaReplyPort.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                "Maximum retry attempts exceeded for PDF signing"
            );
            pdfSignedEventPort.publishPdfSigningFailureNotification(
                command.getSagaId(),
                command.getDocumentId(),
                command.getInvoiceNumber(),
                command.getDocumentType(),
                "Maximum retry attempts exceeded for PDF signing",
                command.getCorrelationId()
            );
            return;
        }

        // 4. Main signing logic — only signing exceptions go to catch block
        try {
            // 4a. Create or retrieve aggregate
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

            // 4b. Start signing (state transition)
            document.startSigning();
            documentRepository.save(document);

            // 4c. Execute signing via hexagonal domain service
            DomainPdfSigningService.SignedPdfResult result = domainPdfSigningService.signPdf(
                command.getPdfUrl(),
                document.getId().toString(),
                padesProperties.getLevel()  // Use configured PAdES level (default: BASELINE_B)
            );

            // 4d. Mark completed (state transition)
            document.markCompleted(
                result.signedPdfPath(),
                result.signedPdfUrl(),
                result.signedPdfSize(),
                result.transactionId(),
                result.certificate(),
                result.signatureLevel(),
                result.signatureTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            );
            documentRepository.save(document);

            // 4e. Send SUCCESS reply AND notification event (dual publishing)
            sagaReplyPort.publishSuccess(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                document.getId().toString(),
                result.signedPdfUrl(),
                result.signedPdfSize(),
                result.transactionId(),
                result.certificate(),
                result.signatureLevel(),
                result.signatureTimestamp()
            );
            pdfSignedEventPort.publishPdfSignedNotification(
                command.getSagaId(),
                command.getDocumentId(),
                command.getInvoiceNumber(),
                command.getDocumentType(),
                document.getId().toString(),
                result.signedPdfUrl(),
                result.signedPdfSize(),
                result.signatureLevel(),
                result.signatureTimestamp(),
                command.getCorrelationId()
            );

            log.info("PDF signing completed successfully for documentId={}, sagaId={}",
                command.getDocumentId(), command.getSagaId());

        } catch (Exception e) {
            // 5. Handle signing failure
            log.error("PDF signing failed for documentId={}, sagaId={}",
                command.getDocumentId(), command.getSagaId(), e);

            // Get the document to mark as failed
            documentRepository.findByInvoiceId(command.getDocumentId()).ifPresent(document -> {
                document.markFailed(e.getMessage());
                document.incrementRetryCount();
                documentRepository.save(document);
            });

            // Send FAILURE reply AND notification event
            sagaReplyPort.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                e.getMessage()
            );
            pdfSignedEventPort.publishPdfSigningFailureNotification(
                command.getSagaId(),
                command.getDocumentId(),
                command.getInvoiceNumber(),
                command.getDocumentType(),
                e.getMessage(),
                command.getCorrelationId()
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
            // 1. Find the signed document
            Optional<SignedPdfDocument> existing = documentRepository.findByInvoiceId(command.getDocumentId());

            if (existing.isPresent()) {
                SignedPdfDocument document = existing.get();

                // 2. Compensate via domain service (deletes from storage and database)
                domainPdfSigningService.compensateSigning(
                    document.getId(),
                    document.getSignedPdfUrl()
                );

                log.info("Compensation completed for document: {}", document.getId());
            } else {
                log.info("No signed document found for documentId={}, compensation already done or never existed",
                    command.getDocumentId());
            }

            // 3. Send COMPENSATED reply (idempotent)
            // Note: No notification event for compensation - orchestrator handles that
            sagaReplyPort.publishCompensated(
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
            sagaReplyPort.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                "Compensation failed: " + e.getMessage()
            );
        }
    }

    /**
     * Sends SUCCESS reply for an already completed document (idempotent).
     */
    private void sendSuccessReply(ProcessPdfSigningCommand command, SignedPdfDocument document) {
        Instant timestamp = document.getSignatureTimestamp()
            .atZone(java.time.ZoneId.systemDefault()).toInstant();
        sagaReplyPort.publishSuccess(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId(),
            document.getId().toString(),
            document.getSignedPdfUrl(),
            document.getSignedPdfSize(),
            document.getTransactionId(),
            document.getCertificate(),
            document.getSignatureLevel(),
            timestamp
        );
        pdfSignedEventPort.publishPdfSignedNotification(
            command.getSagaId(),
            command.getDocumentId(),
            command.getInvoiceNumber(),
            command.getDocumentType(),
            document.getId().toString(),
            document.getSignedPdfUrl(),
            document.getSignedPdfSize(),
            document.getSignatureLevel(),
            timestamp,
            command.getCorrelationId()
        );
    }
}
