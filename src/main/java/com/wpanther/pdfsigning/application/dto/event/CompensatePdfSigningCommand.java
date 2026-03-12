package com.wpanther.pdfsigning.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.SagaCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga command to compensate (rollback) PDF signing.
 * Consumed from: saga.compensation.pdf-signing
 *
 * Sent by the orchestrator when the saga fails and previous steps need to be compensated.
 * Compensation involves deleting the signed PDF and associated records.
 * Extends SagaCommand which provides sagaId, sagaStep, and correlationId.
 */
@Getter
public class CompensatePdfSigningCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("stepToCompensate")
    private final String stepToCompensate;

    /**
     * Constructor for deserialization from Kafka.
     * Used by Apache Camel JSON unmarshalling.
     */
    @JsonCreator
    public CompensatePdfSigningCommand(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("sagaStep") SagaStep sagaStep,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("stepToCompensate") String stepToCompensate
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.documentType = documentType;
        this.stepToCompensate = stepToCompensate;
    }

    /**
     * Convenience constructor for testing.
     */
    public CompensatePdfSigningCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                       String documentId, String documentType, String stepToCompensate) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.documentType = documentType;
        this.stepToCompensate = stepToCompensate;
    }
}
