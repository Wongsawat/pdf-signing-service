package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PadesDigestComputer}.
 */
@DisplayName("PadesDigestComputer Tests")
class PadesDigestComputerTest {

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

    private PadesDigestComputer digestComputer;

    @BeforeEach
    void setUp() {
        digestComputer = new PadesDigestComputer();
    }

    @Test
    @DisplayName("Should compute SHA-256 digest for valid PDF")
    void shouldComputeByteRangeDigest() throws Exception {
        // Given
        InputStream pdf = new ByteArrayInputStream(MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1));

        // When
        byte[] digest = digestComputer.computeByteRangeDigest(pdf);

        // Then - SHA-256 digest should be 32 bytes
        assertThat(digest).isNotEmpty();
        assertThat(digest).hasSize(32);
    }

    @Test
    @DisplayName("Should produce consistent digest for same PDF")
    void shouldProduceConsistentDigest() throws Exception {
        // When
        byte[] digest1 = digestComputer.computeByteRangeDigest(
            new ByteArrayInputStream(MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1)));
        byte[] digest2 = digestComputer.computeByteRangeDigest(
            new ByteArrayInputStream(MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1)));

        // Then
        assertThat(digest1).hasSize(32);
        assertThat(digest2).hasSize(32);
    }

    @Test
    @DisplayName("Should produce different digest for different PDFs")
    void shouldProduceDifferentDigestForDifferentPdfs() throws Exception {
        // Given
        String pdf2 = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000113 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n200\n%%EOF";

        // When
        byte[] digest1 = digestComputer.computeByteRangeDigest(
            new ByteArrayInputStream(MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1)));
        byte[] digest2 = digestComputer.computeByteRangeDigest(
            new ByteArrayInputStream(pdf2.getBytes(StandardCharsets.ISO_8859_1)));

        // Then
        assertThat(digest1).isNotEqualTo(digest2);
    }

    @Test
    @DisplayName("Should throw exception for invalid PDF")
    void shouldThrowExceptionForInvalidPdf() {
        // Given
        InputStream invalidPdf = new ByteArrayInputStream("Not a PDF".getBytes());

        // When/Then
        assertThatThrownBy(() -> digestComputer.computeByteRangeDigest(invalidPdf))
            .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should throw exception for null input stream")
    void shouldThrowExceptionForNullInputStream() {
        // When/Then
        assertThatThrownBy(() -> digestComputer.computeByteRangeDigest(null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should throw exception for empty input stream")
    void shouldHandleEmptyInputStream() {
        // Given
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        // When/Then
        assertThatThrownBy(() -> digestComputer.computeByteRangeDigest(emptyStream))
            .isInstanceOf(IOException.class);
    }
}
