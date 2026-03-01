package com.wpanther.pdfsigning.application.service;

import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import com.wpanther.pdfsigning.domain.model.SignedPdfDocumentId;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.domain.service.PdfSigningService;
import com.wpanther.pdfsigning.domain.service.SignedPdfStorageProvider;
import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SagaCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaCommandHandler Tests")
class SagaCommandHandlerTest {

    @Mock
    private SignedPdfDocumentRepository documentRepository;

    @Mock
    private PdfSigningService signingService;

    @Mock
    private SignedPdfStorageProvider storageProvider;

    @Mock
    private PdfSigningEventPublisher eventPublisher;

    @InjectMocks
    private SagaCommandHandler sagaCommandHandler;

    @Test
    @DisplayName("Should process signing command successfully")
    void shouldProcessSigningCommandSuccessfully() {
        // Given
        ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INV-2024-001", "INVOICE",
            "http://example.com/file.pdf", 12345L, true
        );

        SignedPdfDocument document = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.empty());
        when(documentRepository.save(any(SignedPdfDocument.class))).thenAnswer(invocation -> {
            SignedPdfDocument doc = invocation.getArgument(0);
            // Simulate ID assignment by repository
            return doc;
        });

        PdfSigningService.SignedPdfResult result = new PdfSigningService.SignedPdfResult(
            "/storage/signed/file.pdf",
            "http://example.com/signed.pdf",
            54321L,
            "txn-abc",
            "PEM-CERT",
            "PAdES-BASELINE-T",
            LocalDateTime.now()
        );

        when(signingService.signPdf(eq("http://example.com/file.pdf"), any()))
            .thenReturn(result);

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        verify(documentRepository, times(2)).save(any(SignedPdfDocument.class));
        verify(signingService).signPdf(eq("http://example.com/file.pdf"), any());
        verify(eventPublisher).publishSuccess(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456"),
            eq("doc-789"),
            eq("INV-2024-001"),
            eq("INVOICE"),
            anyString(), // signedDocumentId
            eq("http://example.com/signed.pdf"),
            eq(54321L),
            eq("txn-abc"),
            eq("PEM-CERT"),
            eq("PAdES-BASELINE-T"),
            any() // signatureTimestamp
        );
    }

    @Test
    @DisplayName("Should be idempotent for already completed documents")
    void shouldHandleAlreadyCompletedDocumentIdempotently() {
        // Given
        ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INV-2024-001", "INVOICE",
            "http://example.com/file.pdf", 12345L, true
        );

        // Create a completed document using the factory method and state transitions
        SignedPdfDocument completedDocument = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );
        completedDocument.startSigning();
        completedDocument.markCompleted(
            "/storage/signed/file.pdf",
            "http://example.com/signed.pdf",
            54321L,
            "txn-abc",
            "PEM-CERT",
            "PAdES-BASELINE-T",
            LocalDateTime.now()
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.of(completedDocument));

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then
        verify(signingService, never()).signPdf(any(), any());
        verify(eventPublisher).publishSuccess(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456"),
            eq("doc-789"),
            eq("INV-2024-001"),
            eq("INVOICE"),
            anyString(), // signedDocumentId
            eq("http://example.com/signed.pdf"),
            eq(54321L),
            eq("txn-abc"),
            eq("PEM-CERT"),
            eq("PAdES-BASELINE-T"),
            any() // signatureTimestamp
        );
    }

    @Test
    @DisplayName("Should handle compensation command")
    void shouldHandleCompensationCommand() {
        // Given
        CompensatePdfSigningCommand command = new CompensatePdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INVOICE", "sign-pdf"
        );

        SignedPdfDocument document = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );
        document.startSigning();
        document.markCompleted(
            "/storage/signed/file.pdf",
            "http://example.com/signed.pdf",
            54321L,
            "txn-abc",
            "PEM-CERT",
            "PAdES-BASELINE-T",
            LocalDateTime.now()
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.of(document));
        doNothing().when(documentRepository).deleteById(any());

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then
        verify(documentRepository).deleteById(document.getId());
        verify(eventPublisher).publishCompensated(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456")
        );
    }

    @Test
    @DisplayName("Should handle compensation for non-existent document idempotently")
    void shouldHandleCompensationForNonExistentDocument() {
        // Given
        CompensatePdfSigningCommand command = new CompensatePdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INVOICE", "sign-pdf"
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.empty());

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then
        verify(documentRepository, never()).deleteById(any());
        verify(eventPublisher).publishCompensated(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456")
        );
    }

    @Test
    @DisplayName("Should call handleProcessCommand via SagaCommandPort interface")
    void shouldCallHandleProcessCommandViaInterface() {
        // Given - use the interface method
        ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INV-2024-001", "INVOICE",
            "http://example.com/file.pdf", 12345L, true
        );

        SignedPdfDocument completedDocument = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );
        completedDocument.startSigning();
        completedDocument.markCompleted(
            "/storage/signed/file.pdf",
            "http://example.com/signed.pdf",
            54321L,
            "txn-abc",
            "PEM-CERT",
            "PAdES-BASELINE-T",
            LocalDateTime.now()
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.of(completedDocument));

        // When - call via interface method
        sagaCommandHandler.handleProcessPdfSigning(command);

        // Then - should still work and publish success
        verify(eventPublisher).publishSuccess(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456"),
            eq("doc-789"),
            eq("INV-2024-001"),
            eq("INVOICE"),
            anyString(),
            eq("http://example.com/signed.pdf"),
            eq(54321L),
            eq("txn-abc"),
            eq("PEM-CERT"),
            eq("PAdES-BASELINE-T"),
            any()
        );
    }

    @Test
    @DisplayName("Should call handleCompensation via SagaCommandPort interface")
    void shouldCallHandleCompensationViaInterface() {
        // Given - use the interface method
        CompensatePdfSigningCommand command = new CompensatePdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INVOICE", "sign-pdf"
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.empty());

        // When - call via interface method
        sagaCommandHandler.handleCompensatePdfSigning(command);

        // Then - should still work and publish compensated
        verify(eventPublisher).publishCompensated(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456")
        );
    }

    @Test
    @DisplayName("Should handle max retries exceeded scenario")
    void shouldHandleMaxRetriesExceeded() {
        // Given
        ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INV-2024-001", "INVOICE",
            "http://example.com/file.pdf", 12345L, true
        );

        // Create a failed document with max retries
        SignedPdfDocument failedDocument = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );
        failedDocument.startSigning();
        failedDocument.markFailed("Previous failure");
        // Increment to max retries (default is 3)
        failedDocument.incrementRetryCount();
        failedDocument.incrementRetryCount();
        failedDocument.incrementRetryCount();

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.of(failedDocument));

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then - should publish failure with max retries message
        verify(eventPublisher).publishFailure(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456"),
            eq("doc-789"),
            eq("INV-2024-001"),
            eq("INVOICE"),
            eq("Maximum retry attempts exceeded for PDF signing")
        );
        verify(signingService, never()).signPdf(any(), any());
    }

    @Test
    @DisplayName("Should handle signing service exception")
    void shouldHandleSigningServiceException() {
        // Given
        ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INV-2024-001", "INVOICE",
            "http://example.com/file.pdf", 12345L, true
        );

        SignedPdfDocument document = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.empty());
        when(documentRepository.save(any(SignedPdfDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(signingService.signPdf(any(), any()))
            .thenThrow(new RuntimeException("Signing failed"));

        // When
        sagaCommandHandler.handleProcessCommand(command);

        // Then - should mark document as failed and publish failure event
        verify(eventPublisher).publishFailure(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456"),
            eq("doc-789"),
            eq("INV-2024-001"),
            eq("INVOICE"),
            eq("Signing failed")
        );
    }

    @Test
    @DisplayName("Should handle storage deletion exception during compensation")
    void shouldHandleStorageDeletionException() {
        // Given
        CompensatePdfSigningCommand command = new CompensatePdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INVOICE", "sign-pdf"
        );

        SignedPdfDocument document = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );
        document.startSigning();
        document.markCompleted(
            "/storage/signed/file.pdf",
            "http://example.com/signed.pdf",
            54321L,
            "txn-abc",
            "PEM-CERT",
            "PAdES-BASELINE-T",
            LocalDateTime.now()
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.of(document));
        doThrow(new RuntimeException("Storage delete failed"))
            .when(storageProvider).delete("/storage/signed/file.pdf");
        doNothing().when(documentRepository).deleteById(any());

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then - should still complete compensation and delete from database
        verify(documentRepository).deleteById(document.getId());
        verify(eventPublisher).publishCompensated(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456")
        );
    }

    @Test
    @DisplayName("Should handle exception during compensation")
    void shouldHandleCompensationException() {
        // Given
        CompensatePdfSigningCommand command = new CompensatePdfSigningCommand(
            "saga-123", SagaStep.SIGN_PDF, "corr-456",
            "doc-789", "INVOICE", "sign-pdf"
        );

        SignedPdfDocument document = SignedPdfDocument.create(
            "doc-789", "INV-2024-001",
            "http://example.com/file.pdf", 12345L,
            "corr-456", "INVOICE"
        );
        document.startSigning();
        document.markCompleted(
            "/storage/signed/file.pdf",
            "http://example.com/signed.pdf",
            54321L,
            "txn-abc",
            "PEM-CERT",
            "PAdES-BASELINE-T",
            LocalDateTime.now()
        );

        when(documentRepository.findByInvoiceId("doc-789")).thenReturn(Optional.of(document));
        doThrow(new RuntimeException("Database delete failed"))
            .when(documentRepository).deleteById(any());

        // When
        sagaCommandHandler.handleCompensation(command);

        // Then - should publish failure event
        verify(eventPublisher).publishFailure(
            eq("saga-123"),
            eq(SagaStep.SIGN_PDF),
            eq("corr-456"),
            eq("doc-789"),
            eq(""),
            eq("INVOICE"),
            eq("Compensation failed: Database delete failed")
        );
    }
}
