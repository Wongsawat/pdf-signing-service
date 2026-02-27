package com.wpanther.pdfsigning.domain.service;

import com.wpanther.pdfsigning.domain.model.*;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.domain.port.DocumentDownloadPort;
import com.wpanther.pdfsigning.domain.port.DocumentStoragePort;
import com.wpanther.pdfsigning.domain.port.PdfGenerationPort;
import com.wpanther.pdfsigning.domain.port.SigningPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DomainPdfSigningService}.
 * <p>
 * These tests demonstrate the improved testability of hexagonal architecture:
 * <ul>
 *   <li>No Spring context needed - pure unit tests</li>
 *   <li>Fast execution - no infrastructure dependencies</li>
 *   <li>Easy mocking - all dependencies are port interfaces</li>
 *   <li>Clear testing of domain logic in isolation</li>
 * </ul>
 * </p>
 */
@DisplayName("DomainPdfSigningService Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DomainPdfSigningServiceTest {

    @Mock
    private SigningPort mockSigningPort;

    @Mock
    private PdfGenerationPort mockPdfPort;

    @Mock
    private DocumentStoragePort mockStoragePort;

    @Mock
    private DocumentDownloadPort mockDownloadPort;

    @Mock
    private SignedPdfDocumentRepository mockRepository;

    private DomainPdfSigningService service;

    @BeforeEach
    void setUp() {
        service = new DomainPdfSigningService(
            mockSigningPort,
            mockPdfPort,
            mockStoragePort,
            mockDownloadPort,
            mockRepository
        );
    }

    @Nested
    @DisplayName("signPdf() method")
    class SignPdfMethod {

        @Test
        @DisplayName("Should successfully sign PDF with valid inputs")
        void shouldSignPdfSuccessfully() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_T;
            String correlationId = "corr-456";

            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] digest = new byte[32]; // SHA-256 digest
            byte[] signedPdfBytes = "signed pdf content".getBytes();
            String storageUrl = "https://storage.example.com/signed.pdf";

            // Configure repository to return the same document that was saved
            when(mockDownloadPort.downloadPdf(originalPdfUrl)).thenReturn(pdfBytes);
            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(mockPdfPort.computeByteRangeDigest(pdfBytes)).thenReturn(digest);
            when(mockSigningPort.signPdf(pdfBytes, digest, certChain)).thenReturn(signedPdfBytes);
            when(mockStoragePort.store(eq(signedPdfBytes), eq("SIGNED_PDF"), any(SignedPdfDocument.class))).thenReturn(storageUrl);

            // When
            SignedPdfDocument result = service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            );

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(SigningStatus.COMPLETED);
            assertThat(result.getSignedPdfUrl()).isEqualTo(storageUrl);

            // Verify the complete workflow was executed
            verify(mockSigningPort).validateCertificateChain(certChain);
            verify(mockDownloadPort).downloadPdf(originalPdfUrl);
            verify(mockPdfPort).computeByteRangeDigest(pdfBytes);
            verify(mockSigningPort).signPdf(pdfBytes, digest, certChain);
            verify(mockStoragePort).store(eq(signedPdfBytes), eq("SIGNED_PDF"), any(SignedPdfDocument.class));
            verify(mockRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("Should fail when digest computation throws exception")
        void shouldFailOnDigestException() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_T;
            String correlationId = "corr-456";

            byte[] pdfBytes = "test pdf content".getBytes();

            when(mockDownloadPort.downloadPdf(originalPdfUrl)).thenReturn(pdfBytes);
            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(new SigningException("Digest computation failed"))
                .when(mockPdfPort).computeByteRangeDigest(pdfBytes);

            // When & Then
            assertThatThrownBy(() -> service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            ))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Digest computation failed");

            // Verify download and digest were attempted
            verify(mockDownloadPort).downloadPdf(originalPdfUrl);
            verify(mockPdfPort).computeByteRangeDigest(pdfBytes);
        }

        @Test
        @DisplayName("Should fail when signing throws exception")
        void shouldFailOnSigningException() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_T;
            String correlationId = "corr-456";

            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] digest = new byte[32];

            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(mockDownloadPort.downloadPdf(originalPdfUrl)).thenReturn(pdfBytes);
            when(mockPdfPort.computeByteRangeDigest(pdfBytes)).thenReturn(digest);

            doThrow(new SigningException("CSC API error"))
                .when(mockSigningPort).signPdf(pdfBytes, digest, certChain);

            // When & Then
            assertThatThrownBy(() -> service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            ))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("CSC API error");

            // Verify workflow progressed to signing stage
            verify(mockDownloadPort).downloadPdf(originalPdfUrl);
            verify(mockPdfPort).computeByteRangeDigest(pdfBytes);
        }

        @Test
        @DisplayName("Should fail when storage throws exception")
        void shouldFailOnStorageException() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_T;
            String correlationId = "corr-456";

            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] digest = new byte[32];
            byte[] signedPdfBytes = "signed pdf content".getBytes();

            when(mockDownloadPort.downloadPdf(originalPdfUrl)).thenReturn(pdfBytes);
            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(mockPdfPort.computeByteRangeDigest(pdfBytes)).thenReturn(digest);
            when(mockSigningPort.signPdf(pdfBytes, digest, certChain)).thenReturn(signedPdfBytes);

            doThrow(new StorageException("S3 bucket unavailable"))
                .when(mockStoragePort).store(eq(signedPdfBytes), eq("SIGNED_PDF"), any(SignedPdfDocument.class));

            // When & Then
            assertThatThrownBy(() -> service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            ))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("S3 bucket unavailable");

            // Verify workflow progressed to storage stage
            verify(mockDownloadPort).downloadPdf(originalPdfUrl);
            verify(mockPdfPort).computeByteRangeDigest(pdfBytes);
            verify(mockSigningPort).signPdf(pdfBytes, digest, certChain);
        }

        @Test
        @DisplayName("Should test with BASELINE_B PadesLevel")
        void shouldTestBaselineBLevel() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_B;
            String correlationId = "corr-456";

            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] digest = new byte[32];
            byte[] signedPdfBytes = "signed pdf content".getBytes();
            String storageUrl = "https://storage.example.com/signed.pdf";

            when(mockDownloadPort.downloadPdf(originalPdfUrl)).thenReturn(pdfBytes);
            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(mockPdfPort.computeByteRangeDigest(pdfBytes)).thenReturn(digest);
            when(mockSigningPort.signPdf(pdfBytes, digest, certChain)).thenReturn(signedPdfBytes);
            when(mockStoragePort.store(eq(signedPdfBytes), eq("SIGNED_PDF"), any(SignedPdfDocument.class))).thenReturn(storageUrl);

            // When
            SignedPdfDocument result = service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            );

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSignatureLevel()).isEqualTo("PAdES-BASELINE-B");
        }

        @Test
        @DisplayName("Should fail when certificate validation throws exception")
        void shouldFailOnCertificateValidation() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_T;
            String correlationId = "corr-456";

            doThrow(new SigningException("Certificate expired"))
                .when(mockSigningPort).validateCertificateChain(certChain);

            // When & Then
            assertThatThrownBy(() -> service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            ))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Certificate expired");

            // Verify no further processing was attempted
            verify(mockPdfPort, never()).computeByteRangeDigest(any());
            verify(mockSigningPort, never()).signPdf(any(), any(), any());
        }

        @Test
        @DisplayName("Should validate certificate chain before processing")
        void shouldValidateCertificateChainFirst() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_T;
            String correlationId = "corr-456";

            // Stub repository save to return the same document
            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            doThrow(new SigningException("Invalid certificate"))
                .when(mockSigningPort).validateCertificateChain(certChain);

            // When & Then
            assertThatThrownBy(() -> service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            ))
                .isInstanceOf(SigningException.class);

            // Verify validation was called before any other processing
            verify(mockSigningPort).validateCertificateChain(certChain);
            verify(mockPdfPort, never()).computeByteRangeDigest(any());
        }

        @Test
        @DisplayName("Should fail when download throws exception")
        void shouldFailOnDownloadException() {
            // Given
            String invoiceId = "invoice-123";
            String invoiceNumber = "INV-2024-001";
            String originalPdfUrl = "https://storage.example.com/original.pdf";
            Long originalPdfSize = 1024L;
            X509Certificate[] certChain = new X509Certificate[0];
            PadesLevel padesLevel = PadesLevel.BASELINE_T;
            String correlationId = "corr-456";

            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            doThrow(new SigningException("Network error downloading PDF"))
                .when(mockDownloadPort).downloadPdf(originalPdfUrl);

            // When & Then
            assertThatThrownBy(() -> service.signPdf(
                invoiceId, invoiceNumber, originalPdfUrl, originalPdfSize,
                certChain, padesLevel, correlationId
            ))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Network error downloading PDF");

            // Verify download was attempted but no further processing
            verify(mockDownloadPort).downloadPdf(originalPdfUrl);
            verify(mockPdfPort, never()).computeByteRangeDigest(any());
            verify(mockSigningPort, never()).signPdf(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("compensateSigning() method")
    class CompensateSigningMethod {

        @Test
        @DisplayName("Should successfully compensate by deleting signed PDF")
        void shouldCompensateSuccessfully() {
            // Given
            SignedPdfDocumentId documentId = SignedPdfDocumentId.generate();
            SignedPdfDocument document = SignedPdfDocument.create(
                "invoice-123", "INV-001", "url", 1024L, "corr", "TAX_INVOICE"
            );
            document.startSigning();  // Move to SIGNING state
            document.markCompleted("path", "url", 2048L, "txn", "cert", "PAdES-BASELINE-T", LocalDateTime.now());

            when(mockRepository.findById(documentId)).thenReturn(Optional.of(document));

            // When
            service.compensateSigning(documentId);

            // Then
            verify(mockStoragePort).delete("url");
            verify(mockRepository).deleteById(document.getId());
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void shouldThrowWhenDocumentNotFound() {
            // Given
            SignedPdfDocumentId documentId = SignedPdfDocumentId.generate();
            when(mockRepository.findById(documentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> service.compensateSigning(documentId))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Document not found");

            verify(mockStoragePort, never()).delete(any());
            verify(mockRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Should handle null signed PDF URL gracefully")
        void shouldHandleNullSignedPdfUrl() {
            // Given
            SignedPdfDocumentId documentId = SignedPdfDocumentId.generate();
            SignedPdfDocument document = SignedPdfDocument.create(
                "invoice-123", "INV-001", "url", 1024L, "corr", "TAX_INVOICE"
            );
            // Don't mark as completed - stays in PENDING with no signedPdfUrl

            when(mockRepository.findById(documentId)).thenReturn(Optional.of(document));

            // When
            service.compensateSigning(documentId);

            // Then - should not attempt to delete from storage
            verify(mockStoragePort, never()).delete(any());
            verify(mockRepository).deleteById(document.getId());
        }

        @Test
        @DisplayName("Should handle document in COMPLETED state with URL")
        void shouldHandleCompletedDocument() {
            // Given
            SignedPdfDocumentId documentId = SignedPdfDocumentId.generate();
            SignedPdfDocument document = SignedPdfDocument.create(
                "invoice-123", "INV-001", "url", 1024L, "corr", "TAX_INVOICE"
            );
            document.startSigning();
            document.markCompleted("path", "http://example.com/signed.pdf", 2048L, "txn", "cert", "PAdES-BASELINE-T", LocalDateTime.now());

            when(mockRepository.findById(documentId)).thenReturn(Optional.of(document));

            // When
            service.compensateSigning(documentId);

            // Then
            verify(mockStoragePort).delete("http://example.com/signed.pdf");
            verify(mockRepository).deleteById(document.getId());
        }
    }

    @Nested
    @DisplayName("extractPathFromUrl() helper method")
    class ExtractPathFromUrlMethod {

        @Test
        @DisplayName("Should extract filename from simple URL")
        void shouldExtractFilenameFromSimpleUrl() throws Exception {
            // Given
            service = new DomainPdfSigningService(
                mockSigningPort, mockPdfPort, mockStoragePort, mockDownloadPort, mockRepository
            );

            // Using reflection to test private method
            java.lang.reflect.Method method = DomainPdfSigningService.class
                .getDeclaredMethod("extractPathFromUrl", String.class);
            method.setAccessible(true);

            // When
            String path = (String) method.invoke(service, "https://example.com/signed.pdf");

            // Then
            assertThat(path).isEqualTo("signed.pdf");
        }

        @Test
        @DisplayName("Should extract filename from URL with path")
        void shouldExtractFilenameFromUrlWithPath() throws Exception {
            // Given
            service = new DomainPdfSigningService(
                mockSigningPort, mockPdfPort, mockStoragePort, mockDownloadPort, mockRepository
            );

            java.lang.reflect.Method method = DomainPdfSigningService.class
                .getDeclaredMethod("extractPathFromUrl", String.class);
            method.setAccessible(true);

            // When
            String path = (String) method.invoke(service, "https://storage.example.com/bucket/2024/02/27/signed.pdf");

            // Then
            assertThat(path).isEqualTo("signed.pdf");
        }

        @Test
        @DisplayName("Should extract filename from URL with query params")
        void shouldExtractFilenameFromUrlWithQueryParams() throws Exception {
            // Given
            service = new DomainPdfSigningService(
                mockSigningPort, mockPdfPort, mockStoragePort, mockDownloadPort, mockRepository
            );

            java.lang.reflect.Method method = DomainPdfSigningService.class
                .getDeclaredMethod("extractPathFromUrl", String.class);
            method.setAccessible(true);

            // When
            String path = (String) method.invoke(service, "https://example.com/signed.pdf?token=abc");

            // Then
            assertThat(path).isEqualTo("signed.pdf?token=abc");
        }
    }

    @Nested
    @DisplayName("extractCertificatePem() helper method")
    class ExtractCertificatePemMethod {

        @Test
        @DisplayName("Should return PEM placeholder")
        void shouldReturnPemPlaceholder() throws Exception {
            // Given
            service = new DomainPdfSigningService(
                mockSigningPort, mockPdfPort, mockStoragePort, mockDownloadPort, mockRepository
            );

            java.lang.reflect.Method method = DomainPdfSigningService.class
                .getDeclaredMethod("extractCertificatePem", X509Certificate[].class);
            method.setAccessible(true);

            // Given
            X509Certificate[] certChain = new X509Certificate[0];

            // When
            String pem = (String) method.invoke(service, (Object) certChain);

            // Then
            assertThat(pem).contains("-----BEGIN CERTIFICATE-----");
            assertThat(pem).contains("-----END CERTIFICATE-----");
            assertThat(pem).contains("PLACEHOLDER");
        }
    }
}
