package com.invoice.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.pdfsigning.domain.event.PdfSignedEvent;
import com.invoice.pdfsigning.domain.model.SignedPdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event publisher for PDF signed events.
 *
 * Publishes PdfSignedEvent to the pdf.signed topic after successful PDF signing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.pdf-signed}")
    private String pdfSignedTopic;

    /**
     * Publishes a PdfSignedEvent to Kafka.
     *
     * @param document the signed PDF document
     * @param correlationId correlation ID for tracing
     */
    public void publishPdfSigned(SignedPdfDocument document, String correlationId) {
        try {
            // Build event
            PdfSignedEvent event = PdfSignedEvent.builder()
                    .invoiceId(document.getInvoiceId())
                    .invoiceNumber(document.getInvoiceNumber())
                    .documentType(document.getDocumentType())
                    .signedDocumentId(document.getId().asString())
                    .signedPdfUrl(document.getSignedPdfUrl())
                    .signedPdfSize(document.getSignedPdfSize())
                    .transactionId(document.getTransactionId())
                    .certificate(document.getCertificate())
                    .signatureLevel(document.getSignatureLevel())
                    .signatureTimestamp(document.getSignatureTimestamp())
                    .build();

            // Set base event fields
            event.setEventId(UUID.randomUUID().toString());
            event.setEventType("PdfSigned");
            event.setOccurredAt(LocalDateTime.now());
            event.setVersion("1.0");
            event.setCorrelationId(correlationId);

            // Serialize to JSON
            String eventJson = objectMapper.writeValueAsString(event);

            // Publish to Kafka (using invoiceId as key for partitioning)
            kafkaTemplate.send(pdfSignedTopic, document.getInvoiceId(), eventJson);

            log.info("Published PdfSignedEvent for invoice: {} to topic: {}",
                    document.getInvoiceId(), pdfSignedTopic);
            log.debug("Event payload: {}", eventJson);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PdfSignedEvent for invoice: {}",
                    document.getInvoiceId(), e);
            throw new RuntimeException("Failed to publish PdfSignedEvent", e);
        }
    }
}
