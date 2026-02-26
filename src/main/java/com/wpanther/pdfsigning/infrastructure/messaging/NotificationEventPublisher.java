package com.wpanther.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSignedNotificationEvent;
import com.wpanther.pdfsigning.domain.event.PdfSigningFailedNotificationEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes notification events for the notification-service observer.
 * Notification-service is NOT part of saga - it's a reactive observer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.notification-events:notification.events}")
    private String notificationEventsTopic;

    /**
     * Publish notification event when PDF is signed successfully.
     * This is separate from saga reply - orchestrator doesn't consume this.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfSignedNotification(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            Instant signatureTimestamp,
            String correlationId) {

        PdfSignedNotificationEvent notification = PdfSignedNotificationEvent.create(
            sagaId, invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("eventType", "PdfSigned");
        headers.put("documentType", documentType);
        headers.put("correlationId", correlationId);
        headers.put("invoiceId", invoiceId);

        // Use invoiceId as partition key for all events of the same invoice
        outboxService.saveWithRouting(
            notification,
            "SignedPdfDocument",
            signedDocumentId,
            notificationEventsTopic,
            invoiceId,  // Partition by invoiceId for ordering
            toJson(headers)
        );

        log.info("Published PdfSigned notification for invoiceId={}, invoiceNumber={}, documentType={}",
            invoiceId, invoiceNumber, documentType);
    }

    /**
     * Publish notification event when PDF signing fails.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfSigningFailureNotification(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId) {

        PdfSigningFailedNotificationEvent notification = PdfSigningFailedNotificationEvent.create(
            sagaId, invoiceId, invoiceNumber, documentType,
            errorMessage, correlationId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("eventType", "PdfSigningFailed");
        headers.put("documentType", documentType);
        headers.put("correlationId", correlationId);
        headers.put("invoiceId", invoiceId);

        try {
            outboxService.saveWithRouting(
                notification,
                "SignedPdfDocument",
                invoiceId,
                notificationEventsTopic,
                invoiceId,
                toJson(headers)
            );

            log.warn("Published PdfSigningFailed notification for invoiceId={}, error={}",
                invoiceId, errorMessage);
        } catch (Exception e) {
            log.error("Failed to publish failure notification for invoiceId={}", invoiceId, e);
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize headers to JSON", e);
            return "{}";
        }
    }
}
