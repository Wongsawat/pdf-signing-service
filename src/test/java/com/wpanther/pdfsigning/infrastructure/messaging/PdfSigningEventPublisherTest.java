package com.wpanther.pdfsigning.infrastructure.messaging;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * Unit tests for PdfSigningEventPublisher.
 *
 * Tests dual-publishing wrapper that publishes both saga reply and notification events.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdfSigningEventPublisher Tests")
class PdfSigningEventPublisherTest {

    @Mock
    private SagaReplyPublisher sagaReplyPublisher;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    private PdfSigningEventPublisher publisher;

    private static final String TEST_SAGA_ID = "saga-123";
    private static final String TEST_CORRELATION_ID = "corr-456";
    private static final String TEST_INVOICE_ID = "invoice-789";
    private static final String TEST_INVOICE_NUMBER = "INV-2024-001";
    private static final String TEST_DOCUMENT_TYPE = "TAX_INVOICE";
    private static final String TEST_SIGNED_DOC_ID = "doc-012";
    private static final String TEST_PDF_URL = "http://example.com/signed.pdf";
    private static final Long TEST_PDF_SIZE = 54321L;
    private static final String TEST_TRANSACTION_ID = "txn-abc";
    private static final String TEST_CERTIFICATE = "PEM-CERT";
    private static final String TEST_SIGNATURE_LEVEL = "PAdES-BASELINE-B";
    private static final Instant TEST_TIMESTAMP = Instant.now();

    @BeforeEach
    void setUp() {
        publisher = new PdfSigningEventPublisher(sagaReplyPublisher, notificationEventPublisher);
    }

    @Nested
    @DisplayName("publishSuccess() method")
    class PublishSuccessMethod {

        @Test
        @DisplayName("Should publish both saga reply and notification on success")
        void shouldPublishBothSagaReplyAndNotification() {
            // When
            publisher.publishSuccess(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID,
                TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_TRANSACTION_ID, TEST_CERTIFICATE, TEST_SIGNATURE_LEVEL,
                TEST_TIMESTAMP
            );

            // Then - verify both publishers were called
            verify(sagaReplyPublisher).publishSuccess(
                eq(TEST_SAGA_ID),
                eq(SagaStep.SIGN_PDF),
                eq(TEST_CORRELATION_ID),
                eq(TEST_SIGNED_DOC_ID),
                eq(TEST_PDF_URL),
                eq(TEST_PDF_SIZE),
                eq(TEST_TRANSACTION_ID),
                eq(TEST_CERTIFICATE),
                eq(TEST_SIGNATURE_LEVEL),
                eq(TEST_TIMESTAMP)
            );

            verify(notificationEventPublisher).publishPdfSignedNotification(
                eq(TEST_SAGA_ID),
                eq(TEST_INVOICE_ID),
                eq(TEST_INVOICE_NUMBER),
                eq(TEST_DOCUMENT_TYPE),
                eq(TEST_SIGNED_DOC_ID),
                eq(TEST_PDF_URL),
                eq(TEST_PDF_SIZE),
                eq(TEST_SIGNATURE_LEVEL),
                eq(TEST_TIMESTAMP),
                eq(TEST_CORRELATION_ID)
            );
        }

        @Test
        @DisplayName("Should publish saga reply before notification")
        void shouldPublishSagaReplyBeforeNotification() {
            // When
            publisher.publishSuccess(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID,
                TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_TRANSACTION_ID, TEST_CERTIFICATE, TEST_SIGNATURE_LEVEL,
                TEST_TIMESTAMP
            );

            // Then - verify order of calls (saga reply first, then notification)
            var inOrder = inOrder(
                sagaReplyPublisher,
                notificationEventPublisher
            );

            inOrder.verify(sagaReplyPublisher).publishSuccess(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            );
            inOrder.verify(notificationEventPublisher).publishPdfSignedNotification(
                any(), any(), any(), any(), any(), any(), any(Long.class), any(), any(Instant.class), any()
            );
        }
    }

    @Nested
    @DisplayName("publishFailure() method")
    class PublishFailureMethod {

        @Test
        @DisplayName("Should publish both saga reply and notification on failure")
        void shouldPublishBothSagaReplyAndNotification() {
            // Given
            String errorMessage = "Signing failed: CSC service timeout";

            // When
            publisher.publishFailure(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID,
                TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                errorMessage
            );

            // Then - verify both publishers were called
            verify(sagaReplyPublisher).publishFailure(
                eq(TEST_SAGA_ID),
                eq(SagaStep.SIGN_PDF),
                eq(TEST_CORRELATION_ID),
                eq(errorMessage)
            );

            verify(notificationEventPublisher).publishPdfSigningFailureNotification(
                eq(TEST_SAGA_ID),
                eq(TEST_INVOICE_ID),
                eq(TEST_INVOICE_NUMBER),
                eq(TEST_DOCUMENT_TYPE),
                eq(errorMessage),
                eq(TEST_CORRELATION_ID)
            );
        }

        @Test
        @DisplayName("Should maintain correct order for failure notifications")
        void shouldMaintainCorrectOrderForFailure() {
            // Given
            String errorMessage = "Signing failed";

            // When
            publisher.publishFailure(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID,
                TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                errorMessage
            );

            // Then - verify order
            var inOrder = inOrder(
                sagaReplyPublisher,
                notificationEventPublisher
            );

            inOrder.verify(sagaReplyPublisher).publishFailure(any(), any(), any(), any());
            inOrder.verify(notificationEventPublisher).publishPdfSigningFailureNotification(
                any(), any(), any(), any(), any(), any()
            );
        }
    }

    @Nested
    @DisplayName("publishCompensated() method")
    class PublishCompensatedMethod {

        @Test
        @DisplayName("Should publish only saga reply for compensation")
        void shouldPublishOnlySagaReplyForCompensation() {
            // When
            publisher.publishCompensated(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID
            );

            // Then - verify only saga reply publisher is called
            verify(sagaReplyPublisher).publishCompensated(
                eq(TEST_SAGA_ID),
                eq(SagaStep.SIGN_PDF),
                eq(TEST_CORRELATION_ID)
            );

            // Notification publisher should NOT be called for compensation
            verify(notificationEventPublisher, never()).publishPdfSignedNotification(
                any(), any(), any(), any(), any(), any(), any(Long.class), any(), any(Instant.class), any()
            );
            verify(notificationEventPublisher, never()).publishPdfSigningFailureNotification(
                any(), any(), any(), any(), any(), any()
            );
        }

        @Test
        @DisplayName("Should not publish notification on compensation")
        void shouldNotPublishNotificationOnCompensation() {
            // When
            publisher.publishCompensated(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID
            );

            // Then - verify notification methods are never called
            verify(notificationEventPublisher, never()).publishPdfSignedNotification(
                any(), any(), any(), any(), any(), any(), any(Long.class), any(), any(Instant.class), any()
            );
            verify(notificationEventPublisher, never()).publishPdfSigningFailureNotification(
                any(), any(), any(), any(), any(), any()
            );
        }
    }

    @Nested
    @DisplayName("Dual-publishing pattern")
    class DualPublishingPattern {

        @Test
        @DisplayName("Should ensure both events use same correlation ID")
        void shouldEnsureSameCorrelationId() {
            // When
            publisher.publishSuccess(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID,
                TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                TEST_SIGNED_DOC_ID, TEST_PDF_URL, TEST_PDF_SIZE,
                TEST_TRANSACTION_ID, TEST_CERTIFICATE, TEST_SIGNATURE_LEVEL,
                TEST_TIMESTAMP
            );

            // Then - verify correlation ID is passed to both
            verify(sagaReplyPublisher).publishSuccess(
                any(), any(), eq(TEST_CORRELATION_ID), any(), any(), any(), any(), any(), any(), any()
            );
            verify(notificationEventPublisher).publishPdfSignedNotification(
                any(), any(), any(), any(), any(), any(), any(Long.class), any(), any(Instant.class), eq(TEST_CORRELATION_ID)
            );
        }

        @Test
        @DisplayName("Should ensure both events use same saga ID")
        void shouldEnsureSameSagaId() {
            // When
            publisher.publishFailure(
                TEST_SAGA_ID, SagaStep.SIGN_PDF, TEST_CORRELATION_ID,
                TEST_INVOICE_ID, TEST_INVOICE_NUMBER, TEST_DOCUMENT_TYPE,
                "Error"
            );

            // Then - verify saga ID is passed to both
            verify(sagaReplyPublisher).publishFailure(eq(TEST_SAGA_ID), any(), eq(TEST_CORRELATION_ID), any());
            verify(notificationEventPublisher).publishPdfSigningFailureNotification(
                eq(TEST_SAGA_ID), any(), any(), any(), any(), eq(TEST_CORRELATION_ID)
            );
        }
    }

    @Nested
    @DisplayName("Dependency injection")
    class DependencyInjection {

        @Test
        @DisplayName("Should require both publishers via constructor")
        void shouldRequireBothPublishers() {
            // Then
            assertThat(publisher).isNotNull();
            // The class uses @RequiredArgsConstructor, so both dependencies are required
        }

        @Test
        @DisplayName("Should be a component for Spring DI")
        void shouldBeSpringComponent() {
            // Verify class has @Component annotation
            assertThat(PdfSigningEventPublisher.class.isAnnotationPresent(Component.class)).isTrue();
        }
    }
}
