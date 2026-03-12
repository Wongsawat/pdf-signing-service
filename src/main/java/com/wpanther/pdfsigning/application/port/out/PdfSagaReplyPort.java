package com.wpanther.pdfsigning.application.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

import java.time.Instant;

/**
 * Outbound port for publishing saga reply events to the orchestrator.
 * Replies are sent via outbox pattern to saga.reply.pdf-signing topic.
 */
public interface PdfSagaReplyPort {

    void publishSuccess(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String transactionId,
            String certificate,
            String signatureLevel,
            Instant signatureTimestamp);

    void publishFailure(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String errorMessage);

    void publishCompensated(
            String sagaId,
            SagaStep sagaStep,
            String correlationId);
}
