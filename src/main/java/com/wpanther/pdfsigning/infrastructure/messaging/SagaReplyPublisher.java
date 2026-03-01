package com.wpanther.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSigningReplyEvent;
import com.wpanther.pdfsigning.infrastructure.config.properties.KafkaProperties;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes saga reply events to the orchestrator via outbox pattern.
 * Replies are sent to orchestrator via saga.reply.pdf-signing topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyPublisher {

    private static final String AGGREGATE_TYPE = "SignedPdfDocument";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final KafkaProperties kafkaProperties;

    /**
     * Publish SUCCESS reply when PDF signing completes successfully.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String transactionId,
            String certificate,
            String signatureLevel,
            java.time.Instant signatureTimestamp) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.success(
            sagaId, sagaStep, correlationId,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            transactionId, certificate, signatureLevel, signatureTimestamp
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "SUCCESS");

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            kafkaProperties.getTopics().getSagaReply(),
            sagaId,
            toJson(headers)
        );

        log.info("Published SUCCESS saga reply for sagaId={}, correlationId={}", sagaId, correlationId);
    }

    /**
     * Publish FAILURE reply when PDF signing fails.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.failure(
            sagaId, sagaStep, correlationId, errorMessage
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "FAILURE");

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            kafkaProperties.getTopics().getSagaReply(),
            sagaId,
            toJson(headers)
        );

        log.warn("Published FAILURE saga reply for sagaId={}, correlationId={}, error={}",
            sagaId, correlationId, errorMessage);
    }

    /**
     * Publish COMPENSATED reply when compensation completes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.compensated(
            sagaId, sagaStep, correlationId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "COMPENSATED");

        outboxService.saveWithRouting(
            reply,
            AGGREGATE_TYPE,
            sagaId,
            kafkaProperties.getTopics().getSagaReply(),
            sagaId,
            toJson(headers)
        );

        log.info("Published COMPENSATED saga reply for sagaId={}, correlationId={}", sagaId, correlationId);
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
