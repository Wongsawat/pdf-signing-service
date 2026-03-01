package com.wpanther.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSignedNotificationEvent;
import com.wpanther.pdfsigning.domain.event.PdfSigningFailedNotificationEvent;
import com.wpanther.pdfsigning.infrastructure.config.properties.KafkaProperties;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        publisher = new NotificationEventPublisher(outboxService, new ObjectMapper(), createTestKafkaProperties());
    }

    /**
     * Creates a test KafkaProperties with default values.
     */
    private KafkaProperties createTestKafkaProperties() {
        KafkaProperties props = new KafkaProperties();
        // Use reflection to set the final topics field since Lombok @Data doesn't generate setters for final fields
        try {
            java.lang.reflect.Field topicsField = KafkaProperties.class.getDeclaredField("topics");
            topicsField.setAccessible(true);
            KafkaProperties.Topics topics = new KafkaProperties.Topics();
            topicsField.set(props, topics);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test KafkaProperties", e);
        }
        return props;
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

            // Verify topic and partition key
            assertThat(topicCaptor.getValue()).isEqualTo("notification.events");
            assertThat(partitionKeyCaptor.getValue()).isEqualTo(TEST_INVOICE_ID);

            // Verify headers
            String headersJson = headersCaptor.getValue();
            assertThat(headersJson).contains("\"eventType\":\"PdfSigned\"");
            assertThat(headersJson).contains("\"documentType\":\"" + TEST_DOCUMENT_TYPE + "\"");
        }

        @Test
        @DisplayName("Should include correlationId in headers")
        void shouldIncludeCorrelationIdInHeaders() {
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
            assertThat(headersJson).contains("\"correlationId\":\"" + TEST_CORRELATION_ID + "\"");
        }
    }

    @Nested
    @DisplayName("publishPdfSigningFailureNotification() method")
    class PublishPdfSigningFailureNotificationMethod {

        @Test
        @DisplayName("Should publish PdfSigningFailed notification event")
        void shouldPublishPdfSigningFailureNotification() {
            // Given
            String errorMessage = "CSC service unavailable";

            // When
            publisher.publishPdfSigningFailureNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                errorMessage, TEST_CORRELATION_ID
            );

            // Then
            ArgumentCaptor<PdfSigningFailedNotificationEvent> eventCaptor = ArgumentCaptor.forClass(PdfSigningFailedNotificationEvent.class);
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);

            verify(outboxService).saveWithRouting(
                eventCaptor.capture(),
                eq("SignedPdfDocument"),
                eq(TEST_INVOICE_ID),
                topicCaptor.capture(),
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

            // Verify topic and partition key
            assertThat(topicCaptor.getValue()).isEqualTo("notification.events");
            assertThat(partitionKeyCaptor.getValue()).isEqualTo(TEST_INVOICE_ID);

            // Verify headers
            String headersJson = headersCaptor.getValue();
            assertThat(headersJson).contains("\"eventType\":\"PdfSigningFailed\"");
        }

        @Test
        @DisplayName("Should not throw exception when outbox fails for failure notification")
        void shouldNotThrowWhenOutboxFailsForFailureNotification() {
            // Given
            String errorMessage = "CSC service unavailable";
            doThrow(new RuntimeException("Outbox error"))
                .when(outboxService).saveWithRouting(
                    any(), any(), any(), any(), any(), any()
                );

            // When/Then - should not throw, exception is logged
            publisher.publishPdfSigningFailureNotification(
                TEST_SAGA_ID, TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                errorMessage, TEST_CORRELATION_ID
            );
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("Should use default topic from KafkaProperties")
        void shouldUseDefaultTopic() {
            // When - publisher created with mock KafkaProperties
            // The default topic should be "notification.events"
            assertThat(createTestKafkaProperties().getTopics().getNotificationEvents()).isEqualTo("notification.events");
        }
    }
}
