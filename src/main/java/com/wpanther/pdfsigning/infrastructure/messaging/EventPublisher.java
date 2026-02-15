package com.wpanther.pdfsigning.infrastructure.messaging;

import com.wpanther.pdfsigning.domain.event.PdfSignedEvent;
import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

/**
 * Apache Camel event publisher for PDF signed events.
 *
 * Publishes PdfSignedEvent to the pdf.signed topic after successful PDF signing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final ProducerTemplate producerTemplate;

    /**
     * Publishes a PdfSignedEvent to Kafka via Camel.
     *
     * @param document the signed PDF document
     * @param correlationId correlation ID for tracing
     */
    public void publishPdfSigned(SignedPdfDocument document, String correlationId) {
        // Create event using constructor (base fields auto-generated)
        PdfSignedEvent event = new PdfSignedEvent(
            document.getInvoiceId(),
            document.getInvoiceNumber(),
            document.getDocumentType(),
            document.getId().asString(),
            document.getSignedPdfUrl(),
            document.getSignedPdfSize(),
            document.getTransactionId(),
            document.getCertificate(),
            document.getSignatureLevel(),
            document.getSignatureTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant(),
            correlationId
        );

        // Publish via Camel route (Camel handles JSON marshalling and Kafka key)
        producerTemplate.sendBody("direct:publish-pdf-signed", event);

        log.info("Published PdfSignedEvent for invoice: {} to topic: pdf.signed",
                document.getInvoiceId());
        log.debug("Event payload: invoiceNumber={}, signedDocumentId={}",
                event.getInvoiceNumber(), event.getSignedDocumentId());
    }
}
