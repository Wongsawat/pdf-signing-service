package com.wpanther.pdfsigning.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Saga reply from pdf-signing-service to orchestrator.
 * Published to: saga.reply.pdf-signing (via outbox pattern)
 *
 * Contains the result of PDF signing - either SUCCESS with signed document details,
 * FAILURE with error message, or COMPENSATED after rollback.
 */
public class PdfSigningReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    // Additional fields for successful replies
    private String signedDocumentId;
    private String signedPdfUrl;
    private Long signedPdfSize;
    private String transactionId;
    private String certificate;
    private String signatureLevel;
    private java.time.Instant signatureTimestamp;

    /**
     * Factory method for creating a SUCCESS reply.
     */
    public static PdfSigningReplyEvent success(
            String sagaId, String sagaStep, String correlationId,
            String signedDocumentId, String signedPdfUrl, Long signedPdfSize,
            String transactionId, String certificate, String signatureLevel,
            java.time.Instant signatureTimestamp) {

        PdfSigningReplyEvent reply = new PdfSigningReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
        reply.signedDocumentId = signedDocumentId;
        reply.signedPdfUrl = signedPdfUrl;
        reply.signedPdfSize = signedPdfSize;
        reply.transactionId = transactionId;
        reply.certificate = certificate;
        reply.signatureLevel = signatureLevel;
        reply.signatureTimestamp = signatureTimestamp;
        return reply;
    }

    /**
     * Factory method for creating a FAILURE reply.
     */
    public static PdfSigningReplyEvent failure(
            String sagaId, String sagaStep, String correlationId, String errorMessage) {

        return new PdfSigningReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    /**
     * Factory method for creating a COMPENSATED reply.
     */
    public static PdfSigningReplyEvent compensated(
            String sagaId, String sagaStep, String correlationId) {

        return new PdfSigningReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    // Private constructor for SUCCESS and COMPENSATED
    private PdfSigningReplyEvent(String sagaId, String sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    // Private constructor for FAILURE
    private PdfSigningReplyEvent(String sagaId, String sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }

    // Getters for additional fields (lombok @Getter doesn't work with mutable fields)
    public String getSignedDocumentId() {
        return signedDocumentId;
    }

    public String getSignedPdfUrl() {
        return signedPdfUrl;
    }

    public Long getSignedPdfSize() {
        return signedPdfSize;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getCertificate() {
        return certificate;
    }

    public String getSignatureLevel() {
        return signatureLevel;
    }

    public java.time.Instant getSignatureTimestamp() {
        return signatureTimestamp;
    }
}
