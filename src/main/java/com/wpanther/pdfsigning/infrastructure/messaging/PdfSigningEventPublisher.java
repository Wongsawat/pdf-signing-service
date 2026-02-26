package com.wpanther.pdfsigning.infrastructure.messaging;

import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Convenience wrapper that publishes BOTH saga reply AND notification event.
 * Ensures both events are published in the same transaction via outbox pattern.
 * <p>
 * This implements the dual-publishing pattern:
 * - Saga reply → saga.reply.pdf-signing (for orchestrator coordination)
 * - Notification event → notification.events (for notification-service observer)
 */
@Component
@RequiredArgsConstructor
public class PdfSigningEventPublisher {

    private final SagaReplyPublisher sagaReplyPublisher;
    private final NotificationEventPublisher notificationEventPublisher;

    /**
     * Publish SUCCESS: saga reply to orchestrator + notification to notification-service.
     * Both events are published in the same transaction via outbox pattern.
     */
    public void publishSuccess(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String transactionId,
            String certificate,
            String signatureLevel,
            Instant signatureTimestamp) {

        // 1. Publish saga reply (for orchestrator)
        sagaReplyPublisher.publishSuccess(
            sagaId, sagaStep, correlationId,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            transactionId, certificate, signatureLevel, signatureTimestamp
        );

        // 2. Publish notification event (for notification-service observer)
        notificationEventPublisher.publishPdfSignedNotification(
            sagaId, invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );
    }

    /**
     * Publish FAILURE: saga reply to orchestrator + notification to notification-service.
     * Both events are published in the same transaction via outbox pattern.
     */
    public void publishFailure(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage) {

        // 1. Publish saga reply (for orchestrator)
        sagaReplyPublisher.publishFailure(
            sagaId, sagaStep, correlationId, errorMessage
        );

        // 2. Publish notification event (for notification-service observer)
        notificationEventPublisher.publishPdfSigningFailureNotification(
            sagaId, invoiceId, invoiceNumber, documentType,
            errorMessage, correlationId
        );
    }

    /**
     * Publish COMPENSATED: only saga reply (no notification for compensation).
     * Notification-service doesn't need to know about compensations - orchestrator handles that.
     */
    public void publishCompensated(
            String sagaId,
            SagaStep sagaStep,
            String correlationId) {

        sagaReplyPublisher.publishCompensated(
            sagaId, sagaStep, correlationId
        );
    }
}
