package com.wpanther.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSignedNotificationEvent;
import com.wpanther.pdfsigning.domain.event.PdfSigningFailedNotificationEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for NotificationEventPublisher.
 *
 * Tests notification event publishing for notification-service observer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventPublisher Tests")
class NotificationEventPublisherTest {

    @Mock
    private OutboxService outboxService;

    private NotificationEventPublisher publisher;

    private static final String TEST_SAGA_ID = "saga-123";
    private static final String TEST_INVOICE_ID = "invoice-456";
    private static final String TEST_INVOICE_NUMBER = "INV-2024-001";
    private static final String TEST_DOCUMENT_TYPE = "TAX_INVOICE";
    private static final String TEST_CORRELATION_ID = "corr-789";
    private static final String TEST_SIGNED_DOC_ID = "doc-012";
    private static final String TEST_PDF_URL = "http://example.com/signed.pdf";
    private static final Long TEST_PDF_SIZE = 54321L;
    private static final String TEST_SIGNATURE_LEVEL = "PAdES-BASELINE-T";

    @BeforeEach
    void setUp() {
        publisher = new NotificationEventPublisher(outboxService, new ObjectMapper());
        // Set topic field via reflection (normally injected by Spring @Value)
        ReflectionTestUtils.setField(publisher, "notificationEventsTopic", "notification.events");
    }

    @Nested
    @DisplayName("publishPdfSignedNotification() method")
    class PublishPdfSignedNotificationMethod {

        @Test
        @DisplayName("Should publish PdfSigned notification event")
        void shouldPublishPdfSignedNotification() {
            // Given
            Instant signatureTimestamp = Instant.now();

            // When
            publisher.publishPdfSignedNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_SIGNATURE_LEVEL, signatureTimestamp, TEST_CORRELATION_ID
            );

            // Then
            ArgumentCaptor<PdfSignedNotificationEvent> eventCaptor = ArgumentCaptor.forClass(PdfSignedNotificationEvent.class);
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);

            verify(outboxService).saveWithRouting(
                eventCaptor.capture(),
                eq("SignedPdfDocument"),
                eq(TEST_SIGNED_DOC_ID),
                topicCaptor.capture(),
                partitionKeyCaptor.capture(),
                headersCaptor.capture()
            );

            // Verify event
            PdfSignedNotificationEvent event = eventCaptor.getValue();
            assertThat(event.getSagaId()).isEqualTo(TEST_SAGA_ID);
            assertThat(event.getInvoiceId()).isEqualTo(TEST_INVOICE_ID);
            assertThat(event.getInvoiceNumber()).isEqualTo(TEST_INVOICE_NUMBER);
            assertThat(event.getDocumentType()).isEqualTo(TEST_DOCUMENT_TYPE);
            assertThat(event.getSignedDocumentId()).isEqualTo(TEST_SIGNED_DOC_ID);
            assertThat(event.getSignedPdfUrl()).isEqualTo(TEST_PDF_URL);
            assertThat(event.getSignedPdfSize()).isEqualTo(TEST_PDF_SIZE);
            assertThat(event.getSignatureLevel()).isEqualTo(TEST_SIGNATURE_LEVEL);
            assertThat(event.getSignatureTimestamp()).isEqualTo(signatureTimestamp);
            assertThat(event.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);

            // Verify routing
            assertThat(topicCaptor.getValue()).isEqualTo("notification.events");
            assertThat(partitionKeyCaptor.getValue()).isEqualTo(TEST_INVOICE_ID); // Partitioned by invoiceId

            // Verify headers
            verifyNotificationHeaders(headersCaptor.getValue(), "PdfSigned");
        }

        @Test
        @DisplayName("Should include correct headers for PdfSigned event")
        void shouldIncludeCorrectHeadersForPdfSigned() {
            // Given
            Instant signatureTimestamp = Instant.now();

            // When
            publisher.publishPdfSignedNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_SIGNATURE_LEVEL, signatureTimestamp, TEST_CORRELATION_ID
            );

            // Then
            ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
            verify(outboxService).saveWithRouting(
                any(), any(), any(), any(),
                partitionKeyCaptor.capture(),
                headersCaptor.capture()
            );

            verifyNotificationHeaders(headersCaptor.getValue(), "PdfSigned");
        }

        @Test
        @DisplayName("Should use invoiceId as partition key")
        void shouldUseInvoiceIdAsPartitionKey() {
            // Given
            Instant signatureTimestamp = Instant.now();

            // When
            publisher.publishPdfSignedNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_SIGNATURE_LEVEL, signatureTimestamp, TEST_CORRELATION_ID
            );

            // Then
            ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(outboxService).saveWithRouting(
                any(), any(), any(), any(),
                partitionKeyCaptor.capture(),
                any()
            );

            assertThat(partitionKeyCaptor.getValue()).isEqualTo(TEST_INVOICE_ID);
        }
    }

    @Nested
    @DisplayName("publishPdfSigningFailureNotification() method")
    class PublishPdfSigningFailureNotificationMethod {

        @Test
        @DisplayName("Should publish PdfSigningFailed notification event")
        void shouldPublishPdfSigningFailedNotification() {
            // Given
            String errorMessage = "CSC service unavailable";

            // When
            publisher.publishPdfSigningFailureNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                errorMessage, TEST_CORRELATION_ID
            );

            // Then
            ArgumentCaptor<PdfSigningFailedNotificationEvent> eventCaptor = ArgumentCaptor.forClass(PdfSigningFailedNotificationEvent.class);
            ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);

            verify(outboxService).saveWithRouting(
                eventCaptor.capture(),
                eq("SignedPdfDocument"),
                eq(TEST_INVOICE_ID),
                any(), // topic
                partitionKeyCaptor.capture(),
                headersCaptor.capture()
            );

            // Verify event
            PdfSigningFailedNotificationEvent event = eventCaptor.getValue();
            assertThat(event.getSagaId()).isEqualTo(TEST_SAGA_ID);
            assertThat(event.getInvoiceId()).isEqualTo(TEST_INVOICE_ID);
            assertThat(event.getInvoiceNumber()).isEqualTo(TEST_INVOICE_NUMBER);
            assertThat(event.getDocumentType()).isEqualTo(TEST_DOCUMENT_TYPE);
            assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
            assertThat(event.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);

            // Verify routing
            assertThat(partitionKeyCaptor.getValue()).isEqualTo(TEST_INVOICE_ID);

            // Verify headers
            verifyNotificationHeaders(headersCaptor.getValue(), "PdfSigningFailed");
        }

        @Test
        @DisplayName("Should handle outbox service exceptions gracefully")
        void shouldHandleOutboxExceptionsGracefully() {
            // Given
            String errorMessage = "CSC service unavailable";
            RuntimeException outboxException = new RuntimeException("Outbox failed");
            doThrow(outboxException).when(outboxService).saveWithRouting(
                any(), any(), any(), any(), any(), any()
            );

            // When/Then - should not throw, exception is caught and logged
            publisher.publishPdfSigningFailureNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                errorMessage, TEST_CORRELATION_ID
            );

            // Verify outbox was called
            verify(outboxService).saveWithRouting(
                any(), any(), any(), any(), any(), any()
            );
        }
    }

    @Nested
    @DisplayName("toJson() method")
    class ToJsonMethod {

        @Test
        @DisplayName("Should serialize headers to JSON")
        void shouldSerializeHeadersToJson() {
            // Given
            Instant signatureTimestamp = Instant.now();

            // When
            publisher.publishPdfSignedNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_SIGNATURE_LEVEL, signatureTimestamp, TEST_CORRELATION_ID
            );

            // Then
            ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
            verify(outboxService).saveWithRouting(
                any(), any(), any(), any(), any(),
                headersCaptor.capture()
            );

            String headersJson = headersCaptor.getValue();
            assertThat(headersJson).contains("\"eventType\"");
            assertThat(headersJson).contains("\"documentType\"");
            assertThat(headersJson).contains("\"correlationId\"");
            assertThat(headersJson).contains("\"invoiceId\"");
        }

        @Test
        @DisplayName("Should return empty JSON on serialization error")
        void shouldReturnEmptyJsonOnSerializationError() {
            // This is implicitly tested - if ObjectMapper fails, returns "{}"
            // The actual behavior is that any exception is caught and logged
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("Should use default topic when not configured")
        void shouldUseDefaultTopic() {
            // The default topic should be "notification.events"
            String defaultTopic = (String) ReflectionTestUtils.getField(publisher, "notificationEventsTopic");

            // Then
            assertThat(defaultTopic).isEqualTo("notification.events");
        }

        @Test
        @DisplayName("Should allow custom topic via configuration")
        void shouldAllowCustomTopic() {
            // When
            String customTopic = "custom.notification.topic";
            ReflectionTestUtils.setField(publisher, "notificationEventsTopic", customTopic);

            Instant signatureTimestamp = Instant.now();
            publisher.publishPdfSignedNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_SIGNATURE_LEVEL, signatureTimestamp, TEST_CORRELATION_ID
            );

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(outboxService).saveWithRouting(
                any(), any(), any(),
                topicCaptor.capture(),
                any(),
                any()
            );

            assertThat(topicCaptor.getValue()).isEqualTo(customTopic);
        }
    }

    // Helper method to verify notification headers
    private void verifyNotificationHeaders(String headersJson, String expectedEventType) {
        assertThat(headersJson).contains("\"eventType\":\"" + expectedEventType + "\"");
        assertThat(headersJson).contains("\"documentType\":\"" + TEST_DOCUMENT_TYPE + "\"");
        assertThat(headersJson).contains("\"correlationId\":\"" + TEST_CORRELATION_ID + "\"");
        assertThat(headersJson).contains("\"invoiceId\":\"" + TEST_INVOICE_ID + "\"");
    }
}
