package com.wpanther.pdfsigning.infrastructure.adapter.secondary.storage;

import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import com.wpanther.pdfsigning.domain.model.SignedPdfDocumentId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LocalStorageAdapter}.
 * <p>
 * Tests the local filesystem storage adapter using temporary directories.
 * </p>
 */
@DisplayName("LocalStorageAdapter Tests")
class LocalStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalStorageAdapter(
            tempDir.toString(),
            "http://localhost:8080"
        );
    }

    @Nested
    @DisplayName("store() method")
    class StoreMethod {

        @Test
        @DisplayName("Should store document and return URL")
        void shouldStoreDocument() throws IOException {
            // Given
            byte[] documentData = "test pdf content".getBytes();
            SignedPdfDocument document = createTestDocument();

            // When
            String storageUrl = adapter.store(documentData, "SIGNED_PDF", document);

            // Then
            assertThat(storageUrl).isNotNull();
            assertThat(storageUrl).startsWith("http://localhost:8080");
            assertThat(storageUrl).contains(".pdf");

            // Verify file was created by listing files in tempDir
            try (var files = Files.walk(tempDir)) {
                assertThat(files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".pdf"))
                    .count()).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("Should create date-based directory structure")
        void shouldCreateDateBasedDirectories() {
            // Given
            byte[] documentData = "test pdf".getBytes();
            SignedPdfDocument document = createTestDocument();

            // When
            String storageUrl = adapter.store(documentData, "SIGNED_PDF", document);

            // Then - should have YYYY/MM/DD structure
            String relativePath = storageUrl.substring("http://localhost:8080/documents".length());
            String[] parts = relativePath.split("/");
            assertThat(parts).hasSize(5); // documents/YYYY/MM/DD/filename.pdf
            assertThat(parts[1]).matches("\\d{4}"); // year
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
    }

    @Nested
    @DisplayName("retrieve() method")
    class RetrieveMethod {

        @Test
        @DisplayName("Should retrieve stored document")
        void shouldRetrieveDocument() throws IOException {
            // Given
            byte[] originalData = "test pdf content".getBytes();
            SignedPdfDocument document = createTestDocument();
            Path testFile = tempDir.resolve("test-file.pdf");
            Files.write(testFile, originalData);

            // When
            byte[] retrieved = adapter.retrieve("http://localhost:8080/documents/test-file.pdf");

            // Then
            assertThat(retrieved).isEqualTo(originalData);
        }

        @Test
        @DisplayName("Should throw exception when file not found")
        void shouldThrowWhenFileNotFound() {
            // When/Then
            assertThatThrownBy(() -> adapter.retrieve("http://localhost:8080/documents/nonexistent.pdf"))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.StorageException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should extract key from storage URL correctly")
        void shouldExtractKeyFromUrl() {
            // Given
            byte[] originalData = "test pdf".getBytes();
            SignedPdfDocument document = createTestDocument();

            // When
            String storageUrl = adapter.store(originalData, "SIGNED_PDF", document);

            // When/Then
            byte[] retrieved = adapter.retrieve(storageUrl);
            assertThat(retrieved).isEqualTo(originalData);
        }
    }

    @Nested
    @DisplayName("delete() method")
    class DeleteMethod {

        @Test
        @DisplayName("Should delete existing file")
        void shouldDeleteExistingFile() throws IOException {
            // Given
            byte[] documentData = "test pdf".getBytes();
            SignedPdfDocument document = createTestDocument();
            Path testFile = tempDir.resolve("test-file.pdf");
            Files.write(testFile, documentData);

            // When
            adapter.delete("http://localhost:8080/documents/test-file.pdf");

            // Then
            assertThat(Files.exists(testFile)).isFalse();
        }

        @Test
        @DisplayName("Should handle deletion of non-existent file gracefully")
        void shouldHandleNonExistentFileGracefully() {
            // When/Then - should not throw
            adapter.delete("http://localhost:8080/documents/nonexistent.pdf");
            // Passes if no exception is thrown
        }

        @Test
        @DisplayName("Should delete by direct path when URL doesn't match base URL")
        void shouldDeleteByDirectPath() throws IOException {
            // Given
            Path testFile = tempDir.resolve("direct-file.pdf");
            Files.write(testFile, "content".getBytes());

            // When
            adapter.delete(testFile.toString());

            // Then
            assertThat(Files.exists(testFile)).isFalse();
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
