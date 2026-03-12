package com.wpanther.pdfsigning.application.dto.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PdfSigningReplyEvent.
 */
@DisplayName("PdfSigningReplyEvent Tests")
class PdfSigningReplyEventTest {

    @Test
    @DisplayName("Should create SUCCESS reply with all fields")
    void shouldCreateSuccessReplyWithAllFields() {
        // Given
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.SIGN_PDF;
        String correlationId = "corr-456";
        String signedDocumentId = "signed-doc-789";
        String signedPdfUrl = "http://example.com/signed.pdf";
        Long signedPdfSize = 54321L;
        String transactionId = "txn-abc";
        String certificate = "PEM-CERT";
        String signatureLevel = "PAdES-BASELINE-T";
        Instant signatureTimestamp = Instant.now();

        // When
        PdfSigningReplyEvent reply = PdfSigningReplyEvent.success(
            sagaId, sagaStep, correlationId,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            transactionId, certificate, signatureLevel, signatureTimestamp
        );

        // Then
        assertThat(reply.getSagaId()).isEqualTo(sagaId);
        assertThat(reply.getSagaStep()).isEqualTo(sagaStep);
        assertThat(reply.getCorrelationId()).isEqualTo(correlationId);
        assertThat(reply.getSignedDocumentId()).isEqualTo(signedDocumentId);
        assertThat(reply.getSignedPdfUrl()).isEqualTo(signedPdfUrl);
        assertThat(reply.getSignedPdfSize()).isEqualTo(signedPdfSize);
        assertThat(reply.getTransactionId()).isEqualTo(transactionId);
        assertThat(reply.getCertificate()).isEqualTo(certificate);
        assertThat(reply.getSignatureLevel()).isEqualTo(signatureLevel);
        assertThat(reply.getSignatureTimestamp()).isEqualTo(signatureTimestamp);
        assertThat(reply.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should create FAILURE reply with error message")
    void shouldCreateFailureReply() {
        // Given
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.SIGN_PDF;
        String correlationId = "corr-456";
        String errorMessage = "Signing failed: CSC API timeout";

        // When
        PdfSigningReplyEvent reply = PdfSigningReplyEvent.failure(
            sagaId, sagaStep, correlationId, errorMessage
        );

        // Then
        assertThat(reply.getSagaId()).isEqualTo(sagaId);
        assertThat(reply.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(reply.isFailure()).isTrue();
    }

    @Test
    @DisplayName("Should create COMPENSATED reply")
    void shouldCreateCompensatedReply() {
        // Given
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.SIGN_PDF;
        String correlationId = "corr-456";

        // When
        PdfSigningReplyEvent reply = PdfSigningReplyEvent.compensated(
            sagaId, sagaStep, correlationId
        );

        // Then
        assertThat(reply.getSagaId()).isEqualTo(sagaId);
        assertThat(reply.isCompensated()).isTrue();
    }
}
