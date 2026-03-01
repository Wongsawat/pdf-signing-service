package com.wpanther.pdfsigning.application.service;

import com.wpanther.pdfsigning.application.port.SagaCommandPort;
import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import com.wpanther.pdfsigning.domain.model.*;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.domain.service.DomainPdfSigningService;
import com.wpanther.pdfsigning.infrastructure.config.properties.SigningProperties;
import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PdfSigningEventPublisher eventPublisher;
    private final SigningProperties signingProperties;

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
            if (existing.isPresent() && existing.get().getRetryCount() >= signingProperties.getMaxRetries()) {
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

            // 6. Execute signing via hexagonal domain service
            DomainPdfSigningService.SignedPdfResult result = domainPdfSigningService.signPdf(
                command.getPdfUrl(),
                document.getId().toString(),
                PadesLevel.BASELINE_B  // Default PAdES level for Thai e-Tax compliance
            );

            // 7. Mark completed (state transition)
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

            // 8. Send SUCCESS reply AND notification event (dual publishing)
            eventPublisher.publishSuccess(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                command.getDocumentId(),           // invoiceId
                command.getInvoiceNumber(),
                command.getDocumentType(),
                document.getId().toString(),
                result.signedPdfUrl(),
                result.signedPdfSize(),
                result.transactionId(),
                result.certificate(),
                result.signatureLevel(),
                result.signatureTimestamp()
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
