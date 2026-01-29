package com.invoice.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.pdfsigning.application.service.PdfSigningOrchestrationService;
import com.invoice.pdfsigning.domain.event.PdfGeneratedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for PDF generated events.
 *
 * Listens to the pdf.generated and pdf.signing.requested topics and triggers the PDF signing workflow.
 * Uses manual acknowledgment to ensure reliable message processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfEventListener {

    private final PdfSigningOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    /**
     * Handles PdfGeneratedEvent from Kafka.
     *
     * @param message the raw JSON message
     * @param acknowledgment manual acknowledgment
     */
    @KafkaListener(
            topics = "${app.kafka.topics.pdf-generated}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handlePdfGenerated(
            @Payload String message,
            Acknowledgment acknowledgment) {

        try {
            log.info("Received PdfGeneratedEvent from Kafka");
            log.debug("Event payload: {}", message);

            // Parse event
            PdfGeneratedEvent event = objectMapper.readValue(message, PdfGeneratedEvent.class);

            log.info("Processing PDF signing for invoice: {}", event.getInvoiceId());

            // Delegate to orchestration service
            orchestrationService.processSigningRequest(event);

            // Acknowledge message only after successful processing
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged PDF signing for invoice: {}",
                    event.getInvoiceId());

        } catch (Exception e) {
            log.error("Failed to process PdfGeneratedEvent: {}", message, e);
            // Do NOT acknowledge - message will be retried
            // The orchestration service should have saved the error state
        }
    }

    /**
     * Handles PDF signing requests from the unified topic.
     *
     * @param message the raw JSON message
     * @param acknowledgment manual acknowledgment
     */
    @KafkaListener(
            topics = "${app.kafka.topics.pdf-signing-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handlePdfSigningRequested(
            @Payload String message,
            Acknowledgment acknowledgment) {

        try {
            log.info("Received PDF signing request from unified topic");
            log.debug("Event payload: {}", message);

            // Parse event
            PdfGeneratedEvent event = objectMapper.readValue(message, PdfGeneratedEvent.class);

            log.info("Processing PDF signing for invoice: {} (documentType: {})",
                    event.getInvoiceId(), event.getDocumentType());

            // Delegate to orchestration service (same processing)
            orchestrationService.processSigningRequest(event);

            // Acknowledge message only after successful processing
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged PDF signing for invoice: {}",
                    event.getInvoiceId());

        } catch (Exception e) {
            log.error("Failed to process PDF signing request: {}", message, e);
            // Do NOT acknowledge - message will be retried
        }
    }
}
