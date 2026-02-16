package com.wpanther.pdfsigning.infrastructure.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PadesSignatureEmbedder.
 *
 * Tests PDF digest computation, CMS signature building, and signature embedding.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PadesSignatureEmbedder Tests")
class PadesSignatureEmbedderTest {

    @InjectMocks
    private PadesSignatureEmbedder embedder;

    @Test
    @DisplayName("Should compute byte range digest for PDF")
    void shouldComputeByteRangeDigest() throws Exception {
        // Given - sample PDF bytes (minimal valid PDF)
        InputStream pdf = getSamplePdfInputStream();

        // When
        byte[] digest = embedder.computeByteRangeDigest(pdf);

        // Then - SHA-256 digest should be 32 bytes
        assertThat(digest).isNotEmpty();
        assertThat(digest).hasSize(32);
    }

    @Test
    @DisplayName("Should throw exception for invalid PDF when computing digest")
    void shouldThrowExceptionForInvalidPdf() {
        // Given
        InputStream invalidPdf = new ByteArrayInputStream("Not a PDF".getBytes());

        // When/Then
        assertThatThrownBy(() -> embedder.computeByteRangeDigest(invalidPdf))
            .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should throw exception when embedding signature into invalid PDF")
    void shouldThrowExceptionForInvalidPdfWhenEmbedding() {
        // Given
        byte[] invalidPdf = "Not a PDF".getBytes();
        byte[] cmsSignature = new byte[32];

        // When/Then
        assertThatThrownBy(() -> embedder.embedSignature(invalidPdf, cmsSignature))
            .isInstanceOf(IOException.class);
    }

    // Helper methods

    private InputStream getSamplePdfInputStream() {
        // Minimal valid PDF
        String minimalPdf = "%PDF-1.4\n" +
            "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
            "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n" +
            "3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<<>>>>endobj\n" +
            "xref\n" +
            "0 4\n" +
            "0000000000 65535 f\n" +
            "0000000009 00000 n\n" +
            "0000000056 00000 n\n" +
            "0000000115 00000 n\n" +
            "trailer<</Size 4/Root 1 0 R>>\n" +
            "startxref\n" +
            "210\n" +
            "%%EOF";
        return new ByteArrayInputStream(minimalPdf.getBytes(StandardCharsets.ISO_8859_1));
    }
}
