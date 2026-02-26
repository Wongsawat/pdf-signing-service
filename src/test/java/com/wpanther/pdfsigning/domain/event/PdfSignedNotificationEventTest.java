package com.wpanther.pdfsigning.domain.event;

import com.wpanther.saga.domain.model.TraceEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PdfSignedNotificationEvent.
 */
@DisplayName("PdfSignedNotificationEvent Tests")
class PdfSignedNotificationEventTest {

    @Test
    @DisplayName("Should create notification event with all fields")
    void shouldCreateNotificationEvent() {
        // Given
        String sagaId = "saga-123";
        String invoiceId = "inv-123";
        String invoiceNumber = "INV-2024-001";
        String documentType = "INVOICE";
        String signedDocumentId = "signed-doc-789";
        String signedPdfUrl = "http://example.com/signed.pdf";
        Long signedPdfSize = 54321L;
        String signatureLevel = "PAdES-BASELINE-T";
        Instant signatureTimestamp = Instant.now();
        String correlationId = "corr-456";

        // When
        PdfSignedNotificationEvent event = PdfSignedNotificationEvent.create(
            sagaId, invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );

        // Then
        assertThat(event).isInstanceOf(TraceEvent.class);
        assertThat(event.getTraceType()).isEqualTo("PdfSigned");
        assertThat(event.getSagaId()).isEqualTo(sagaId);
        assertThat(event.getSource()).isEqualTo("pdf-signing-service");
        assertThat(event.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(event.getInvoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(event.getDocumentType()).isEqualTo(documentType);
        assertThat(event.getSignedDocumentId()).isEqualTo(signedDocumentId);
        assertThat(event.getSignedPdfUrl()).isEqualTo(signedPdfUrl);
        assertThat(event.getSignedPdfSize()).isEqualTo(signedPdfSize);
        assertThat(event.getSignatureLevel()).isEqualTo(signatureLevel);
        assertThat(event.getSignatureTimestamp()).isEqualTo(signatureTimestamp);
        assertThat(event.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Should create failure notification event")
    void shouldCreateFailureNotification() {
        // Given
        String sagaId = "saga-123";
        String invoiceId = "inv-123";
        String invoiceNumber = "INV-2024-001";
        String documentType = "INVOICE";
        String errorMessage = "Signing failed: CSC API timeout";
        String correlationId = "corr-456";

        // When
        PdfSigningFailedNotificationEvent event = PdfSigningFailedNotificationEvent.create(
            sagaId, invoiceId, invoiceNumber, documentType,
            errorMessage, correlationId
        );

        // Then
        assertThat(event).isInstanceOf(TraceEvent.class);
        assertThat(event.getTraceType()).isEqualTo("PdfSigningFailed");
        assertThat(event.getSagaId()).isEqualTo(sagaId);
        assertThat(event.getSource()).isEqualTo("pdf-signing-service");
        assertThat(event.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(event.getInvoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(event.getDocumentType()).isEqualTo(documentType);
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(event.getCorrelationId()).isEqualTo(correlationId);
    }
}
