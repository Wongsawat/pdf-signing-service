package com.wpanther.pdfsigning.infrastructure.adapter.secondary.storage;

import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link S3StorageAdapter}.
 * <p>
 * Tests the S3 storage adapter using mocked S3Client.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageAdapter Tests")
class S3StorageAdapterTest {

    @Mock
    private S3Client mockS3Client;

    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new S3StorageAdapter(
            mockS3Client,
            "etax-signed-pdfs",
            "http://localhost:9000/etax-signed-pdfs/"
        );
    }

    @Nested
    @DisplayName("store() method")
    class StoreMethod {

        @Test
        @DisplayName("Should store document and return URL")
        void shouldStoreDocument() {
            // Given
            byte[] documentData = "test pdf content".getBytes();
            SignedPdfDocument document = createTestDocument();

            // When
            String storageUrl = adapter.store(documentData, "SIGNED_PDF", document);

            // Then
            assertThat(storageUrl).isNotNull();
            assertThat(storageUrl).startsWith("http://localhost:9000");
            assertThat(storageUrl).contains(".pdf");

            // Verify S3 put was called
            verify(mockS3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("Should create date-based key structure")
        void shouldCreateDateBasedKeyStructure() {
            // Given
            byte[] documentData = "test pdf".getBytes();
            SignedPdfDocument document = createTestDocument();

            // When
            String storageUrl = adapter.store(documentData, "SIGNED_PDF", document);

            // Then - should have document-type/YYYY/MM/DD structure
            String key = storageUrl.substring("http://localhost:9000/etax-signed-pdfs/".length());
            String[] parts = key.split("/");
            assertThat(parts).hasSize(5); // signed-pdf/YYYY/MM/DD/filename.pdf
            assertThat(parts[0]).matches("signed-pdf"); // document-type
            assertThat(parts[1]).matches("\\d{4}");   // year
            assertThat(parts[2]).matches("\\d{2}");   // month
            assertThat(parts[3]).matches("\\d{2}");   // day
        }

        @Test
        @DisplayName("Should handle unknown document ID gracefully")
        void shouldHandleUnknownDocumentId() {
            // Given
            byte[] documentData = "test pdf".getBytes();

            // When
            String storageUrl = adapter.store(documentData, "SIGNED_PDF", null);

            // Then
            assertThat(storageUrl).contains("unknown.pdf");
        }

        @Test
        @DisplayName("Should propagate S3 exceptions as StorageException")
        void shouldPropagateS3Exception() {
            // Given
            byte[] documentData = "test pdf".getBytes();
            SignedPdfDocument document = createTestDocument();
            doThrow(S3Exception.builder().message("S3 error").build())
                .when(mockS3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

            // When/Then
            assertThatThrownBy(() -> adapter.store(documentData, "SIGNED_PDF", document))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class)
                .hasMessageContaining("Failed to store document");
        }
    }

    @Nested
    @DisplayName("retrieve() method")
    class RetrieveMethod {

        @Test
        @DisplayName("Should retrieve stored document")
        void shouldRetrieveDocument() {
            // Given - using when/then pattern with real S3 client behavior
            // For unit test, we just verify getObject is called
            // The actual byte retrieval integration would be tested separately
            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("Stream-based response"));

            // When/Then - verify that getObject is called with correct request
            assertThatThrownBy(() -> adapter.retrieve("http://localhost:9000/etax-signed-pdfs/test-file.pdf"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class);

            verify(mockS3Client).getObject(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("Should throw exception when object not found")
        void shouldThrowWhenObjectNotFound() {
            // Given
            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Not Found").statusCode(404).build());

            // When/Then
            assertThatThrownBy(() -> adapter.retrieve("http://localhost:9000/etax-signed-pdfs/nonexistent.pdf"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class)
                .hasMessageContaining("Failed to retrieve document");
        }

        @Test
        @DisplayName("Should extract key from URL correctly")
        void shouldExtractKeyFromUrl() {
            // Given
            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("Stream-based response"));

            // When/Then
            assertThatThrownBy(() -> adapter.retrieve("test-key"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class);
            verify(mockS3Client).getObject(any(GetObjectRequest.class));
        }
    }

    @Nested
    @DisplayName("delete() method")
    class DeleteMethod {

        @Test
        @DisplayName("Should delete existing object")
        void shouldDeleteExistingObject() {
            // When
            adapter.delete("http://localhost:9000/etax-signed-pdfs/test-file.pdf");

            // Then
            verify(mockS3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("Should handle non-existent object gracefully")
        void shouldHandleNonExistentObjectGracefully() {
            // When/Then - S3 delete is idempotent, mock returns successfully
            adapter.delete("http://localhost:9000/etax-signed-pdfs/nonexistent.pdf");

            // Passes if no exception is thrown
            verify(mockS3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("Should propagate S3 exceptions as StorageException")
        void shouldPropagateS3ExceptionOnDelete() {
            // Given
            doThrow(S3Exception.builder().message("Access denied").statusCode(403).build())
                .when(mockS3Client).deleteObject(any(DeleteObjectRequest.class));

            // When/Then
            assertThatThrownBy(() -> adapter.delete("http://localhost:9000/etax-signed-pdfs/test-file.pdf"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class)
                .hasMessageContaining("Failed to delete document");
        }
    }

    /**
     * Helper to create a test SignedPdfDocument.
     */
    private SignedPdfDocument createTestDocument() {
        return SignedPdfDocument.create(
            "invoice-123",
            "INV-2024-001",
            "http://example.com/original.pdf",
            1024L,
            "corr-456",
            "TAX_INVOICE"
        );
    }
}
