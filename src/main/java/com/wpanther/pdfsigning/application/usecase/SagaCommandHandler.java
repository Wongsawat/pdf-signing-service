package com.wpanther.pdfsigning.application.usecase;

import com.wpanther.pdfsigning.application.dto.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.application.dto.event.ProcessPdfSigningCommand;
import com.wpanther.pdfsigning.application.port.out.PdfSignedEventPort;
import com.wpanther.pdfsigning.application.port.out.PdfSagaReplyPort;
import com.wpanther.pdfsigning.domain.model.*;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.infrastructure.config.properties.PadesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        if (existing.isPresent() && existing.get().hasExhaustedRetries()) {
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
                result.signatureTimestamp()
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

        } catch (SigningException | StorageException e) {
            // 5. Handle expected business exceptions
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
        } catch (Exception e) {
            // 5b. Handle unexpected exceptions (may indicate bugs)
            log.error("Unexpected error during PDF signing for documentId={}, sagaId={}",
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
     * Deletes the signed document from storage and database, then sends COMPENSATED reply.
     *
     * <p>COMPENSATED is sent via {@link TransactionSynchronization#afterCommit()} so it
     * only fires if the entire transaction (including the DB delete) commits. If the
     * transaction rolls back, FAILURE is sent instead. This ensures the saga orchestrator
     * receives an accurate result even though storage and DB deletions are not atomic
     * (storage commits independently).</p>
     *
     * <p>On retry after a partial failure (storage deleted, DB delete threw): the storage
     * delete is idempotent (S3/filesystem delete is a no-op if already gone) and the
     * DB delete will succeed, so the saga converges to COMPENSATED.</p>
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

            // 3. Register COMPENSATED to fire only after transaction commits.
            // If deleteById or any prior step throws, afterCommit is never registered
            // and the catch block below sends FAILURE instead.
            // Note: No notification event for compensation - orchestrator handles that.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sagaReplyPort.publishCompensated(
                            command.getSagaId(),
                            command.getSagaStep(),
                            command.getCorrelationId()
                        );
                        log.info("Compensation committed, COMPENSATED reply sent for sagaId={}, documentId={}",
                            command.getSagaId(), command.getDocumentId());
                    }
                });
            } else {
                // No active transaction — can send COMPENSATED immediately (unit test context)
                sagaReplyPort.publishCompensated(
                    command.getSagaId(),
                    command.getSagaStep(),
                    command.getCorrelationId()
                );
            }

        } catch (Exception e) {
            log.error("Failed to compensate PDF signing for sagaId={}, documentId={}",
                command.getSagaId(), command.getDocumentId(), e);

            // Send FAILURE reply if compensation fails — transaction will roll back
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
        Instant timestamp = document.getSignatureTimestamp();
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
