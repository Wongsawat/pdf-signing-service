package com.wpanther.pdfsigning.infrastructure.adapter.primary.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.port.in.SagaCommandPort;
import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Primary adapter for Kafka-based saga command consumption.
 * <p>
 * This adapter "drives" the application by consuming saga commands
 * from Kafka and delegating to {@link SagaCommandPort}.
 * </p>
 * <p>
 * In hexagonal architecture terms, this is a "primary" or "driver" adapter
 * that initiates actions in the application from external triggers (Kafka).
 * </p>
 * <p>
 * NOTE: This service contains the handler methods that would be called
 * by the Kafka consumer (e.g., Apache Camel routes in SagaRouteConfig).
 * The actual Kafka consumption is configured in SagaRouteConfig which
 * delegates to these methods.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandKafkaAdapter {

    private final SagaCommandPort sagaCommandPort;
    private final ObjectMapper objectMapper;

    /**
     * Handles saga commands from the orchestrator.
     * <p>
     * Called by the Kafka consumer (SagaRouteConfig) when a message
     * is received on the saga.command.pdf-signing topic.
     * </p>
     *
     * @param commandJson JSON command payload
     */
    public void handlePdfSigningCommand(String commandJson) {
        log.debug("Processing saga command JSON: {}", commandJson);

        try {
            ProcessPdfSigningCommand command = objectMapper.readValue(commandJson, ProcessPdfSigningCommand.class);
            log.info("Processing saga command for saga: {}, invoice: {}",
                command.getSagaId(), command.getInvoiceNumber());

            sagaCommandPort.handleProcessPdfSigning(command);

            log.info("Successfully processed saga command for sagaId: {}", command.getSagaId());

        } catch (Exception e) {
            log.error("Failed to process saga command: {}", commandJson, e);
            throw new RuntimeException("Failed to process saga command", e);
        }
    }

    /**
     * Handles compensation commands from the orchestrator.
     * <p>
     * Called by the Kafka consumer (SagaRouteConfig) when a message
     * is received on the saga.compensation.pdf-signing topic.
     * </p>
     *
     * @param commandJson JSON command payload
     */
    public void handleCompensationCommand(String commandJson) {
        log.debug("Processing compensation command JSON: {}", commandJson);

        try {
            CompensatePdfSigningCommand command = objectMapper.readValue(commandJson, CompensatePdfSigningCommand.class);
            log.info("Processing compensation for saga: {}, document: {}",
                command.getSagaId(), command.getDocumentId());

            sagaCommandPort.handleCompensatePdfSigning(command);

            log.info("Successfully processed compensation command for sagaId: {}", command.getSagaId());

        } catch (Exception e) {
            log.error("Failed to process compensation command: {}", commandJson, e);
            throw new RuntimeException("Failed to process compensation command", e);
        }
    }
}
