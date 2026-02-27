package com.wpanther.pdfsigning.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for domain service value objects and exceptions.
 * Tests SignedPdfResult, SignedPdfStorageProvider components, and exceptions.
 */
@DisplayName("Domain Service DTO and Exception Tests")
class DomainServiceTests {

    @Nested
    @DisplayName("PdfSigningService.SignedPdfResult Tests")
    class SignedPdfResultTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuildWithAllFields() {
            // Given
            LocalDateTime timestamp = LocalDateTime.now();

            // When
            PdfSigningService.SignedPdfResult result = PdfSigningService.SignedPdfResult.builder()
                .signedPdfPath("/path/signed.pdf")
                .signedPdfUrl("http://example.com/signed.pdf")
                .signedPdfSize(2048L)
                .transactionId("txn-123")
                .certificate("-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----")
                .signatureLevel("PAdES-BASELINE-T")
                .signatureTimestamp(timestamp)
                .build();

            // Then
            assertThat(result.getSignedPdfPath()).isEqualTo("/path/signed.pdf");
            assertThat(result.getSignedPdfUrl()).isEqualTo("http://example.com/signed.pdf");
            assertThat(result.getSignedPdfSize()).isEqualTo(2048L);
            assertThat(result.getTransactionId()).isEqualTo("txn-123");
            assertThat(result.getCertificate()).contains("test");
            assertThat(result.getSignatureLevel()).isEqualTo("PAdES-BASELINE-T");
            assertThat(result.getSignatureTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("Should build with required fields only")
        void shouldBuildWithRequiredFields() {
            // When
            PdfSigningService.SignedPdfResult result = PdfSigningService.SignedPdfResult.builder()
                .signedPdfPath("/path/signed.pdf")
                .signedPdfUrl("http://example.com/signed.pdf")
                .build();

            // Then
            assertThat(result.getSignedPdfPath()).isEqualTo("/path/signed.pdf");
            assertThat(result.getSignedPdfUrl()).isEqualTo("http://example.com/signed.pdf");
            assertThat(result.getSignedPdfSize()).isNull();
            assertThat(result.getTransactionId()).isNull();
            assertThat(result.getCertificate()).isNull();
            assertThat(result.getSignatureLevel()).isNull();
            assertThat(result.getSignatureTimestamp()).isNull();
        }

        @Test
        @DisplayName("Should support setters")
        void shouldSupportSetters() {
            // Given
            PdfSigningService.SignedPdfResult result = new PdfSigningService.SignedPdfResult();

            // When
            result.setSignedPdfPath("/new-path/signed.pdf");
            result.setSignedPdfUrl("http://new.example.com/signed.pdf");
            result.setSignedPdfSize(4096L);
            result.setTransactionId("txn-456");
            result.setCertificate("new-cert");
            result.setSignatureLevel("PAdES-BASELINE-B");
            result.setSignatureTimestamp(LocalDateTime.now());

            // Then
            assertThat(result.getSignedPdfPath()).isEqualTo("/new-path/signed.pdf");
            assertThat(result.getSignedPdfUrl()).isEqualTo("http://new.example.com/signed.pdf");
            assertThat(result.getSignedPdfSize()).isEqualTo(4096L);
            assertThat(result.getTransactionId()).isEqualTo("txn-456");
            assertThat(result.getCertificate()).isEqualTo("new-cert");
            assertThat(result.getSignatureLevel()).isEqualTo("PAdES-BASELINE-B");
        }

        @Test
        @DisplayName("Should handle no-args constructor")
        void shouldHandleNoArgsConstructor() {
            // When
            PdfSigningService.SignedPdfResult result = new PdfSigningService.SignedPdfResult();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSignedPdfPath()).isNull();
            assertThat(result.getSignedPdfUrl()).isNull();
        }

        @Test
        @DisplayName("Should handle all-args constructor")
        void shouldHandleAllArgsConstructor() {
            // Given
            LocalDateTime timestamp = LocalDateTime.now();

            // When
            PdfSigningService.SignedPdfResult result = new PdfSigningService.SignedPdfResult(
                "/path/signed.pdf",
                "http://example.com/signed.pdf",
                2048L,
                "txn-123",
                "cert-pem",
                "PAdES-BASELINE-T",
                timestamp
            );

            // Then
            assertThat(result.getSignedPdfPath()).isEqualTo("/path/signed.pdf");
            assertThat(result.getSignedPdfUrl()).isEqualTo("http://example.com/signed.pdf");
            assertThat(result.getSignedPdfSize()).isEqualTo(2048L);
            assertThat(result.getTransactionId()).isEqualTo("txn-123");
            assertThat(result.getCertificate()).isEqualTo("cert-pem");
            assertThat(result.getSignatureLevel()).isEqualTo("PAdES-BASELINE-T");
            assertThat(result.getSignatureTimestamp()).isEqualTo(timestamp);
        }
    }

    @Nested
    @DisplayName("PdfSigningService.PdfSigningException Tests")
    class PdfSigningExceptionTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateWithMessage() {
            // When
            PdfSigningService.PdfSigningException exception = new PdfSigningService.PdfSigningException("Signing failed");

            // Then
            assertThat(exception.getMessage()).isEqualTo("Signing failed");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateWithMessageAndCause() {
            // Given
            Throwable cause = new RuntimeException("Root cause");

            // When
            PdfSigningService.PdfSigningException exception = new PdfSigningService.PdfSigningException("Signing failed", cause);

            // Then
            assertThat(exception.getMessage()).isEqualTo("Signing failed");
            assertThat(exception.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("Should be throwable and catchable as PdfSigningException")
        void shouldBeThrowable() {
            // When/Then
            Exception caught = null;
            try {
                throw new PdfSigningService.PdfSigningException("Test exception");
            } catch (PdfSigningService.PdfSigningException e) {
                caught = e;
            }

            assertThat(caught).isNotNull();
            assertThat(caught.getMessage()).isEqualTo("Test exception");
        }

        @Test
        @DisplayName("Should be catchable as RuntimeException")
        void shouldBeCatchableAsRuntimeException() {
            // When/Then
            Exception caught = null;
            try {
                throw new PdfSigningService.PdfSigningException("Test exception");
            } catch (RuntimeException e) {
                caught = e;
            }

            assertThat(caught).isNotNull();
            assertThat(caught).isInstanceOf(PdfSigningService.PdfSigningException.class);
        }
    }

    @Nested
    @DisplayName("SignedPdfStorageProvider.StorageResult Tests")
    class StorageResultTests {

        @Test
        @DisplayName("Should create with path and url")
        void shouldCreateWithPathAndUrl() {
            // When
            SignedPdfStorageProvider.StorageResult result = new SignedPdfStorageProvider.StorageResult(
                "/path/signed.pdf",
                "http://example.com/signed.pdf"
            );

            // Then
            assertThat(result.path()).isEqualTo("/path/signed.pdf");
            assertThat(result.url()).isEqualTo("http://example.com/signed.pdf");
        }

        @Test
        @DisplayName("Should support record pattern matching")
        void shouldSupportPatternMatching() {
            // Given
            SignedPdfStorageProvider.StorageResult result = new SignedPdfStorageProvider.StorageResult(
                "/path/signed.pdf",
                "http://example.com/signed.pdf"
            );

            // When/Then
            if (result instanceof SignedPdfStorageProvider.StorageResult(String path, String url)) {
                assertThat(path).isEqualTo("/path/signed.pdf");
                assertThat(url).isEqualTo("http://example.com/signed.pdf");
            } else {
                throw new AssertionError("Expected StorageResult");
            }
        }

        @Test
        @DisplayName("Should deconstruct using accessor methods")
        void shouldDeconstruct() {
            // Given
            SignedPdfStorageProvider.StorageResult result = new SignedPdfStorageProvider.StorageResult(
                "/path/signed.pdf",
                "http://example.com/signed.pdf"
            );

            // When
            String path = result.path();
            String url = result.url();

            // Then
            assertThat(path).isEqualTo("/path/signed.pdf");
            assertThat(url).isEqualTo("http://example.com/signed.pdf");
        }

        @Test
        @DisplayName("Should implement equals() correctly")
        void shouldImplementEquals() {
            // Given
            SignedPdfStorageProvider.StorageResult result1 = new SignedPdfStorageProvider.StorageResult(
                "/path/signed.pdf",
                "http://example.com/signed.pdf"
            );
            SignedPdfStorageProvider.StorageResult result2 = new SignedPdfStorageProvider.StorageResult(
                "/path/signed.pdf",
                "http://example.com/signed.pdf"
            );
            SignedPdfStorageProvider.StorageResult result3 = new SignedPdfStorageProvider.StorageResult(
                "/other/signed.pdf",
                "http://other.example.com/signed.pdf"
            );

            // Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1).hasSameHashCodeAs(result2);
            assertThat(result1).isNotEqualTo(result3);
        }
    }

    @Nested
    @DisplayName("SignedPdfStorageProvider.StorageException Tests")
    class StorageExceptionTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateWithMessage() {
            // When
            SignedPdfStorageProvider.StorageException exception = new SignedPdfStorageProvider.StorageException("Storage failed");

            // Then
            assertThat(exception.getMessage()).isEqualTo("Storage failed");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateWithMessageAndCause() {
            // Given
            Throwable cause = new RuntimeException("Disk full");

            // When
            SignedPdfStorageProvider.StorageException exception = new SignedPdfStorageProvider.StorageException(
                "Storage failed",
                cause
            );

            // Then
            assertThat(exception.getMessage()).isEqualTo("Storage failed");
            assertThat(exception.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("Should be throwable and catchable as StorageException")
        void shouldBeThrowable() {
            // When/Then
            Exception caught = null;
            try {
                throw new SignedPdfStorageProvider.StorageException("Test exception");
            } catch (SignedPdfStorageProvider.StorageException e) {
                caught = e;
            }

            assertThat(caught).isNotNull();
            assertThat(caught.getMessage()).isEqualTo("Test exception");
        }

        @Test
        @DisplayName("Should be catchable as RuntimeException")
        void shouldBeCatchableAsRuntimeException() {
            // When/Then
            Exception caught = null;
            try {
                throw new SignedPdfStorageProvider.StorageException("Test exception");
            } catch (RuntimeException e) {
                caught = e;
            }

            assertThat(caught).isNotNull();
            assertThat(caught).isInstanceOf(SignedPdfStorageProvider.StorageException.class);
        }
    }

    @Nested
    @DisplayName("PdfSigningService interface contract")
    class PdfSigningServiceInterfaceTests {

        @Test
        @DisplayName("Should define signPdf method signature")
        void shouldDefineSignPdfMethod() {
            // This test verifies the interface contract exists
            // In a real scenario, implementation classes would implement this
            PdfSigningService service = new PdfSigningService() {
                @Override
                public SignedPdfResult signPdf(String pdfUrl, String documentId) {
                    return SignedPdfResult.builder().build();
                }
            };

            assertThat(service).isNotNull();
        }

        @Test
        @DisplayName("SignedPdfResult should be accessible as inner class")
        void signedPdfResultShouldBeAccessible() {
            // Verify the inner class is properly accessible
            Class<?> resultClass = PdfSigningService.SignedPdfResult.class;
            assertThat(resultClass.getSimpleName()).isEqualTo("SignedPdfResult");
        }

        @Test
        @DisplayName("PdfSigningException should be accessible as inner class")
        void pdfSigningExceptionShouldBeAccessible() {
            // Verify the inner class is properly accessible
            Class<?> exceptionClass = PdfSigningService.PdfSigningException.class;
            assertThat(exceptionClass.getSimpleName()).isEqualTo("PdfSigningException");
            assertThat(RuntimeException.class.isAssignableFrom(exceptionClass)).isTrue();
        }
    }
}
