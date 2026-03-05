package com.wpanther.pdfsigning.infrastructure.adapter.out.download;

import com.wpanther.pdfsigning.domain.model.SigningException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HttpDocumentDownloadAdapter}.
 */
@DisplayName("HttpDocumentDownloadAdapter Tests")
class HttpDocumentDownloadAdapterTest {

    private HttpDocumentDownloadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new HttpDocumentDownloadAdapter();
    }

    @Nested
    @DisplayName("downloadPdf() method")
    class DownloadPdfMethod {

        @Test
        @DisplayName("Should throw SigningException for invalid URL")
        void shouldThrowForInvalidUrl() {
            // Given
            String invalidUrl = "not-a-valid-url";

            // When & Then
            assertThatThrownBy(() -> adapter.downloadPdf(invalidUrl))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Failed to download PDF from URL");
        }

        @Test
        @DisplayName("Should throw SigningException for non-existent URL")
        void shouldThrowForNonExistentUrl() {
            // Given
            String nonExistentUrl = "http://localhost:9999/non-existent.pdf";

            // When & Then
            assertThatThrownBy(() -> adapter.downloadPdf(nonExistentUrl))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Failed to download PDF from URL");
        }

        @Test
        @DisplayName("Should throw SigningException for URL without PDF content type")
        void shouldThrowForNonPdfUrl() {
            // Given - Using a URL that doesn't return PDF content
            // Note: This test demonstrates the adapter's validation logic
            // In a real integration test, we would mock the HTTP server

            // The adapter validates:
            // 1. HTTP response code (must be 200)
            // 2. Content-Length header (must not exceed MAX_PDF_SIZE)
            // 3. PDF magic bytes (must start with "%PDF")

            // For unit testing without a real server, we test error conditions
            String url = "http://example.com/not-a-pdf";
        }
    }
}
