package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PadesEmbedder}.
 */
@DisplayName("PadesEmbedder Tests")
class PadesEmbedderTest {

    // Minimal valid PDF for testing
    private static final String MINIMAL_PDF =
        "%PDF-1.4\n" +
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

    private PadesEmbedder pdfEmbedder;

    @BeforeEach
    void setUp() {
        pdfEmbedder = new PadesEmbedder();
    }

    @Test
    @DisplayName("Should embed CMS signature into PDF")
    void shouldEmbedSignature() throws Exception {
        // Given
        byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);
        byte[] cmsSignature = createSampleCmsSignature();

        // When
        byte[] signedPdf = pdfEmbedder.embedSignature(pdfBytes, cmsSignature);

        // Then
        assertThat(signedPdf).isNotEmpty();
        assertThat(signedPdf.length).isGreaterThan(pdfBytes.length);
    }

    @Test
    @DisplayName("Should produce signed PDF starting with %PDF header")
    void shouldProduceValidPdfHeader() throws Exception {
        // Given
        byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);
        byte[] cmsSignature = createSampleCmsSignature();

        // When
        byte[] signedPdf = pdfEmbedder.embedSignature(pdfBytes, cmsSignature);

        // Then
        assertThat(new String(signedPdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should throw IOException for invalid PDF input")
    void shouldThrowExceptionForInvalidPdf() {
        // Given
        byte[] invalidPdf = "Not a PDF".getBytes();
        byte[] cmsSignature = new byte[32];

        // When/Then
        assertThatThrownBy(() -> pdfEmbedder.embedSignature(invalidPdf, cmsSignature))
            .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException for null PDF bytes")
    void shouldThrowExceptionForNullPdfBytes() {
        // Given
        byte[] cmsSignature = new byte[32];

        // When/Then
        assertThatThrownBy(() -> pdfEmbedder.embedSignature(null, cmsSignature))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException for null CMS signature")
    void shouldThrowExceptionForNullCmsSignature() {
        // Given
        byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);

        // When/Then
        assertThatThrownBy(() -> pdfEmbedder.embedSignature(pdfBytes, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle empty CMS signature without throwing")
    void shouldHandleEmptyCmsSignature() throws Exception {
        // Given
        byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);
        byte[] emptySignature = new byte[0];

        // When
        byte[] result = pdfEmbedder.embedSignature(pdfBytes, emptySignature);

        // Then
        assertThat(result).isNotEmpty();
    }

    private byte[] createSampleCmsSignature() {
        byte[] cmsSig = new byte[512];
        for (int i = 0; i < cmsSig.length; i++) {
            cmsSig[i] = (byte) (i % 256);
        }
        return cmsSig;
    }
}
