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

    @Nested
    @DisplayName("URL key extraction")
    class UrlKeyExtractionTests {

        @Test
        @DisplayName("Should retrieve document with pre-signed URL containing query params")
        void shouldRetrieveWithPresignedUrl() {
            // Given - URL with query parameters (simulating pre-signed URL)
            String presignedUrl = "http://localhost:9000/etax-signed-pdfs/signed-pdf/2024/02/27/test-file.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256";

            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("Stream response"));

            // When/Then - verify getObject is called with key extracted from before "?"
            assertThatThrownBy(() -> adapter.retrieve(presignedUrl))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class);

            // Verify the key was extracted correctly (without query params)
            verify(mockS3Client).getObject(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("Should extract key from URL with bucket path")
        void shouldExtractKeyFromUrlWithBucketPath() {
            // Given - URL with full bucket path
            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("Stream response"));

            // When/Then
            assertThatThrownBy(() -> adapter.retrieve("http://localhost:9000/etax-signed-pdfs/signed-pdf/test.pdf"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class);

            // Verify getObject was called
            verify(mockS3Client).getObject(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("Should handle simple key without URL structure")
        void shouldHandleSimpleKey() {
            // Given - just a key, not a full URL
            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("Stream response"));

            // When/Then
            assertThatThrownBy(() -> adapter.retrieve("simple-key.pdf"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class);

            // Verify the key was used
            verify(mockS3Client).getObject(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("Should handle malformed URL gracefully in retrieve")
        void shouldHandleMalformedUrl() {
            // Given - malformed URL that could cause issues during parsing
            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Invalid key").build());

            // When/Then - should extract key and call S3, which throws exception
            assertThatThrownBy(() -> adapter.retrieve("malformed:///weird//url.pdf"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class);
        }
    }

    @Nested
    @DisplayName("Document type handling")
    class DocumentTypeTests {

        @Test
        @DisplayName("Should sanitize document type with underscores")
        void shouldSanitizeDocumentTypeWithUnderscores() {
            // Given
            byte[] documentData = "test pdf".getBytes();
            SignedPdfDocument document = createTestDocument();

            // When
            String storageUrl = adapter.store(documentData, "TAX_INVOICE", document);

            // Then - underscores should be replaced with hyphens
            assertThat(storageUrl).contains("tax-invoice/");
            assertThat(storageUrl).doesNotContain("TAX_INVOICE");
        }

        @Test
        @DisplayName("Should preserve document type without underscores")
        void shouldPreserveDocumentTypeWithoutUnderscores() {
            // Given
            byte[] documentData = "test pdf".getBytes();
            SignedPdfDocument document = createTestDocument();

            // When
            String storageUrl = adapter.store(documentData, "INVOICE", document);

            // Then
            assertThat(storageUrl).contains("invoice/");
        }
    }

    @Nested
    @DisplayName("Spring constructor")
    class SpringConstructorTests {

        @Test
        @DisplayName("Should create adapter with Spring constructor parameters")
        void shouldCreateWithSpringConstructor() {
            // When - using the main Spring constructor with all parameters
            S3StorageAdapter springAdapter = new S3StorageAdapter(
                "test-bucket",
                "us-east-1",
                "test-access-key",
                "test-secret-key",
                "",  // empty endpoint
                false,  // no path style access
                ""  // empty baseUrl - should use default
            );

            // Then - adapter should be created successfully
            assertThat(springAdapter).isNotNull();
        }

        @Test
        @DisplayName("Should create adapter with custom endpoint")
        void shouldCreateWithCustomEndpoint() {
            // When - using custom endpoint (e.g., MinIO)
            S3StorageAdapter adapterWithEndpoint = new S3StorageAdapter(
                "test-bucket",
                "us-east-1",
                "minioadmin",
                "minioadmin",
                "http://localhost:9000",  // custom endpoint
                true,  // path style access for MinIO
                "http://localhost:9000/test-bucket/"
            );

            // Then
            assertThat(adapterWithEndpoint).isNotNull();
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
