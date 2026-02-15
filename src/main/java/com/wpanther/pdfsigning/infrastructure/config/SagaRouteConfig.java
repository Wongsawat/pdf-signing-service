package com.wpanther.pdfsigning.infrastructure.config;

import com.wpanther.pdfsigning.application.service.SagaCommandHandler;
import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for saga command and compensation consumers.
 * Saga orchestration mode: consumes from orchestrator topics.
 *
 * Consumes from:
 * - saga.command.pdf-signing (from orchestrator - ProcessPdfSigningCommand)
 * - saga.compensation.pdf-signing (from orchestrator - CompensatePdfSigningCommand)
 *
 * Produces to:
 * - saga.reply.pdf-signing (via outbox + Debezium CDC to orchestrator)
 * - notification.events (via outbox + Debezium CDC to notification-service)
 */
@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final SagaCommandHandler sagaCommandHandler;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command:saga.command.pdf-signing}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation:saga.compensation.pdf-signing}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.dlq:pdf.signing.dlq}")
    private String dlqTopic;

    public SagaRouteConfig(SagaCommandHandler sagaCommandHandler) {
        this.sagaCommandHandler = sagaCommandHandler;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler - Dead Letter Channel with retries
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
                        .maximumRedeliveries(3)
                        .redeliveryDelay(1000)
                        .useExponentialBackOff()
                        .backOffMultiplier(2)
                        .maximumRedeliveryDelay(10000)
                        .logExhausted(true)
                        .logStackTrace(true));

        // ============================================================
        // CONSUMER ROUTE: saga.command.pdf-signing (from orchestrator)
        // ============================================================
        from("kafka:" + sagaCommandTopic
                        + "?brokers=" + kafkaBrokers
                        + "&groupId=pdf-signing-service"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError=true"
                        + "&maxPollRecords=100"
                        + "&consumersCount=3")
                        .routeId("saga-command-consumer")
                        .log("Received saga command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                        .unmarshal().json(JsonLibrary.Jackson, ProcessPdfSigningCommand.class)
                        .process(exchange -> {
                                ProcessPdfSigningCommand cmd = exchange.getIn().getBody(ProcessPdfSigningCommand.class);
                                log.info("Processing saga command for saga: {}, invoice: {}",
                                                cmd.getSagaId(), cmd.getInvoiceNumber());
                                sagaCommandHandler.handleProcessCommand(cmd);
                        })
                        .log("Successfully processed saga command for sagaId: ${body.sagaId}");

        // ============================================================
        // CONSUMER ROUTE: saga.compensation.pdf-signing (from orchestrator)
        // ============================================================
        from("kafka:" + sagaCompensationTopic
                        + "?brokers=" + kafkaBrokers
                        + "&groupId=pdf-signing-service"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError=true"
                        + "&maxPollRecords=100"
                        + "&consumersCount=3")
                        .routeId("saga-compensation-consumer")
                        .log("Received compensation command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                        .unmarshal().json(JsonLibrary.Jackson, CompensatePdfSigningCommand.class)
                        .process(exchange -> {
                                CompensatePdfSigningCommand cmd = exchange.getIn().getBody(CompensatePdfSigningCommand.class);
                                log.info("Processing compensation for saga: {}, document: {}",
                                                cmd.getSagaId(), cmd.getDocumentId());
                                sagaCommandHandler.handleCompensation(cmd);
                        })
                        .log("Successfully processed compensation command for sagaId: ${body.sagaId}");
    }
}
