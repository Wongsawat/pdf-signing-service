package com.invoice.pdfsigning.infrastructure.config;

import com.invoice.pdfsigning.application.service.PdfSigningOrchestrationService;
import com.invoice.pdfsigning.domain.event.PdfGeneratedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for PDF signing.
 * Replaces Spring Kafka consumer and producer configuration.
 *
 * Consumes from:
 * - kafka:pdf.generated (from invoice-pdf-generation-service, taxinvoice-pdf-generation-service)
 * - kafka:pdf.signing.requested (unified topic)
 *
 * Produces to:
 * - kafka:pdf.signed (to document-storage-service, notification-service)
 */
@Component
@Slf4j
public class PdfSigningRouteConfig extends RouteBuilder {

    private final PdfSigningOrchestrationService orchestrationService;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.pdf-generated}")
    private String pdfGeneratedTopic;

    @Value("${app.kafka.topics.pdf-signing-requested}")
    private String pdfSigningRequestedTopic;

    @Value("${app.kafka.topics.pdf-signed}")
    private String pdfSignedTopic;

    @Value("${app.kafka.topics.dlq:pdf.signing.dlq}")
    private String dlqTopic;

    public PdfSigningRouteConfig(PdfSigningOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler - Dead Letter Channel with exponential backoff
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(10000)
            .logExhausted(true)
            .logStackTrace(true));

        // ============================================================
        // CONSUMER ROUTE 1: pdf.generated
        // ============================================================
        from("kafka:" + pdfGeneratedTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=pdf-signing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=10"
                + "&consumersCount=3")
            .routeId("pdf-generated-consumer")
            .log("Received PdfGeneratedEvent: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")

            // Unmarshal JSON to PdfGeneratedEvent
            .unmarshal().json(JsonLibrary.Jackson, PdfGeneratedEvent.class)

            // Process the event - call application service
            .process(exchange -> {
                PdfGeneratedEvent event = exchange.getIn().getBody(PdfGeneratedEvent.class);
                log.info("Processing PDF signing for invoice: {}", event.getInvoiceId());

                orchestrationService.processSigningRequest(event);
            })

            .log("Successfully processed PDF signing for invoice: ${body.invoiceId}");

        // ============================================================
        // CONSUMER ROUTE 2: pdf.signing.requested (unified topic)
        // ============================================================
        from("kafka:" + pdfSigningRequestedTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=pdf-signing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=10"
                + "&consumersCount=3")
            .routeId("pdf-signing-requested-consumer")
            .log("Received PdfSigningRequestedEvent: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")

            // Unmarshal JSON to PdfGeneratedEvent
            .unmarshal().json(JsonLibrary.Jackson, PdfGeneratedEvent.class)

            // Process the event - call application service
            .process(exchange -> {
                PdfGeneratedEvent event = exchange.getIn().getBody(PdfGeneratedEvent.class);
                log.info("Processing PDF signing for invoice: {} (documentType: {})",
                        event.getInvoiceId(), event.getDocumentType());

                orchestrationService.processSigningRequest(event);
            })

            .log("Successfully processed PDF signing for invoice: ${body.invoiceId}");

        // ============================================================
        // PRODUCER ROUTE: pdf.signed
        // ============================================================
        from("direct:publish-pdf-signed")
            .routeId("pdf-signed-producer")
            .log("Publishing PdfSignedEvent: ${body.invoiceNumber}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + pdfSignedTopic + "?brokers=" + kafkaBrokers + "&key=${body.invoiceId}")
            .log("Published PdfSignedEvent to: " + pdfSignedTopic);
    }
}
