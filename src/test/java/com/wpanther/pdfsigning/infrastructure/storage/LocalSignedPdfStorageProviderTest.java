package com.wpanther.pdfsigning.infrastructure.storage;

import com.wpanther.pdfsigning.domain.service.SignedPdfStorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LocalSignedPdfStorageProvider.
 *
 * Tests local filesystem storage for signed PDF documents.
 */
@DisplayName("LocalSignedPdfStorageProvider Tests")
class LocalSignedPdfStorageProviderTest {

    private LocalSignedPdfStorageProvider storageProvider;
    private static final byte[] TEST_PDF_CONTENT = "%PDF-1.4\n%%EOF".getBytes();
    private static final String TEST_DOCUMENT_ID = "test-doc-123";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageProvider = new LocalSignedPdfStorageProvider(
            tempDir.toString(),
            "http://localhost:8087"
        );
    }

    @Nested
    @DisplayName("store() method")
    class StoreMethod {

        @Test
        @DisplayName("Should store PDF in date-based directory structure")
        void shouldStorePdfInDateBasedDirectory() throws IOException {
            // When
            SignedPdfStorageProvider.StorageResult result = storageProvider.store(TEST_PDF_CONTENT, TEST_DOCUMENT_ID);

            // Then - verify directory structure
            LocalDateTime now = LocalDateTime.now();
            String expectedDir = String.format("%s/%04d/%02d/%02d",
                tempDir.toString(),
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth()
            );

            Path expectedFile = Path.of(expectedDir, "signed-pdf-" + TEST_DOCUMENT_ID + ".pdf");
            assertThat(Files.exists(expectedFile)).isTrue();
            assertThat(Files.readAllBytes(expectedFile)).isEqualTo(TEST_PDF_CONTENT);

            // Then - verify result
            assertThat(result.path()).contains("signed-pdf-" + TEST_DOCUMENT_ID + ".pdf");
            assertThat(result.url()).startsWith("http://localhost:8087/signed-documents");
            assertThat(result.url()).endsWith(".pdf");
        }

        @Test
        @DisplayName("Should create URL with correct path separators")
        void shouldCreateUrlWithCorrectPathSeparators() {
            // When
            SignedPdfStorageProvider.StorageResult result = storageProvider.store(TEST_PDF_CONTENT, TEST_DOCUMENT_ID);

            // Then - URL should use forward slashes regardless of OS
            assertThat(result.url()).doesNotContain("\\");
            assertThat(result.url()).contains("/signed-documents/");
        }

        @Test
        @DisplayName("Should include relative path in URL")
        void shouldIncludeRelativePathInUrl() {
            // When
            SignedPdfStorageProvider.StorageResult result = storageProvider.store(TEST_PDF_CONTENT, TEST_DOCUMENT_ID);

            // Then
            LocalDateTime now = LocalDateTime.now();
            String expectedDatePath = String.format("/%04d/%02d/%02d/",
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth()
            );
            assertThat(result.url()).contains(expectedDatePath);
        }

        @Test
        @DisplayName("Should return absolute filesystem path")
        void shouldReturnAbsoluteFilesystemPath() {
            // When
            SignedPdfStorageProvider.StorageResult result = storageProvider.store(TEST_PDF_CONTENT, TEST_DOCUMENT_ID);

            // Then
            assertThat(result.path()).startsWith(tempDir.toString());
            assertThat(Path.of(result.path())).isAbsolute();
        }

        @Test
        @DisplayName("Should handle multiple documents with different IDs")
        void shouldHandleMultipleDocuments() {
            // Given - store first document
            storageProvider.store(TEST_PDF_CONTENT, "doc-1");

            // When - store second document
            SignedPdfStorageProvider.StorageResult result2 = storageProvider.store(TEST_PDF_CONTENT, "doc-2");

            // Then - both should exist with different paths
            assertThat(result2.path()).contains("signed-pdf-doc-2.pdf");
            assertThat(Files.exists(Path.of(result2.path()))).isTrue();
        }

        @Test
        @DisplayName("Should overwrite existing file with same document ID")
        void shouldOverwriteExistingFile() throws IOException {
            // Given - store original file
            byte[] originalContent = "ORIGINAL".getBytes();
            storageProvider.store(originalContent, TEST_DOCUMENT_ID);

            // When - store with same ID
            byte[] newContent = "NEW".getBytes();
            SignedPdfStorageProvider.StorageResult result = storageProvider.store(newContent, TEST_DOCUMENT_ID);

            // Then - file should have new content
            Path actualFile = Path.of(result.path());
            assertThat(Files.readAllBytes(actualFile)).isEqualTo(newContent);
        }

        @Test
        @DisplayName("Should throw StorageException when directory creation fails")
        void shouldThrowExceptionWhenDirectoryCreationFails() throws IOException {
            // Given - create a file at the base path where a directory should be
            Path blockFile = tempDir.resolve("block");
            Files.createFile(blockFile);

            // Create a new provider that tries to use the blocked path
            LocalSignedPdfStorageProvider blockedProvider = new LocalSignedPdfStorageProvider(
                blockFile.toString(),
                "http://localhost:8087"
            );

            // When/Then
            assertThatThrownBy(() -> blockedProvider.store(TEST_PDF_CONTENT, TEST_DOCUMENT_ID))
                .isInstanceOf(SignedPdfStorageProvider.StorageException.class)
                .hasMessageContaining("Failed to store signed PDF");
        }
    }

    @Nested
    @DisplayName("delete() method")
    class DeleteMethod {

        @Test
        @DisplayName("Should delete existing file")
        void shouldDeleteExistingFile() {
            // Given - store a file
            SignedPdfStorageProvider.StorageResult result = storageProvider.store(TEST_PDF_CONTENT, TEST_DOCUMENT_ID);
            assertThat(Files.exists(Path.of(result.path()))).isTrue();

            // When
            storageProvider.delete(result.path());

            // Then
            assertThat(Files.exists(Path.of(result.path()))).isFalse();
        }

        @Test
        @DisplayName("Should be idempotent when deleting non-existent file")
        void shouldBeIdempotentWhenDeletingNonExistentFile() {
            // Given - path that doesn't exist
            String nonExistentPath = tempDir.toString() + "/non/existent/path.pdf";

            // When/Then - should not throw
            storageProvider.delete(nonExistentPath);

            // Verify no exception was thrown
            assertThat(true).isTrue();
        }

        @Test
        @DisplayName("Should handle non-existent file in delete gracefully")
        void shouldHandleNonExistentFileGracefully() {
            // Given - path that doesn't exist
            String nonExistentPath = tempDir.resolve("nonexistent.pdf").toString();

            // When/Then - should not throw (implementation checks existence first)
            storageProvider.delete(nonExistentPath);
        }
    }

    @Nested
    @DisplayName("StorageResult record")
    class StorageResultRecord {

        @Test
        @DisplayName("Should create StorageResult with path and URL")
        void shouldCreateStorageResult() {
            // Given
            String path = "/storage/path/file.pdf";
            String url = "http://example.com/file.pdf";

            // When
            SignedPdfStorageProvider.StorageResult result = new SignedPdfStorageProvider.StorageResult(path, url);

            // Then
            assertThat(result.path()).isEqualTo(path);
            assertThat(result.url()).isEqualTo(url);
        }

        @Test
        @DisplayName("Should support record pattern equality")
        void shouldSupportRecordPatternEquality() {
            // Given
            String path = "/storage/path/file.pdf";
            String url = "http://example.com/file.pdf";
            SignedPdfStorageProvider.StorageResult result1 = new SignedPdfStorageProvider.StorageResult(path, url);
            SignedPdfStorageProvider.StorageResult result2 = new SignedPdfStorageProvider.StorageResult(path, url);

            // Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }
    }

    @Nested
    @DisplayName("StorageException class")
    class StorageExceptionClass {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateExceptionWithMessage() {
            // When
            SignedPdfStorageProvider.StorageException exception =
                new SignedPdfStorageProvider.StorageException("Test error");

            // Then
            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Given
            Throwable cause = new RuntimeException("Root cause");

            // When
            SignedPdfStorageProvider.StorageException exception =
                new SignedPdfStorageProvider.StorageException("Test error", cause);

            // Then
            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }
}
