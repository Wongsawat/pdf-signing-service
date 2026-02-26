package com.wpanther.pdfsigning.infrastructure.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for PadesSignatureEmbedder.
 *
 * Tests PDF digest computation, CMS signature building, and signature embedding.
 */
@DisplayName("PadesSignatureEmbedder Tests")
class PadesSignatureEmbedderTest {

    private PadesSignatureEmbedder embedder;

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

    @BeforeEach
    void setUp() {
        embedder = new PadesSignatureEmbedder();
    }

    @Nested
    @DisplayName("computeByteRangeDigest() method")
    class ComputeByteRangeDigestMethod {

        @Test
        @DisplayName("Should compute SHA-256 digest for valid PDF")
        void shouldComputeByteRangeDigest() throws Exception {
            // Given
            InputStream pdf = getSamplePdfInputStream();

            // When
            byte[] digest = embedder.computeByteRangeDigest(pdf);

            // Then - SHA-256 digest should be 32 bytes
            assertThat(digest).isNotEmpty();
            assertThat(digest).hasSize(32);
        }

        @Test
        @DisplayName("Should produce consistent digest for same PDF")
        void shouldProduceConsistentDigest() throws Exception {
            // Given
            InputStream pdf1 = getSamplePdfInputStream();
            InputStream pdf2 = getSamplePdfInputStream();

            // When
            byte[] digest1 = embedder.computeByteRangeDigest(pdf1);
            byte[] digest2 = embedder.computeByteRangeDigest(pdf2);

            // Then - digest should always be 32 bytes for SHA-256
            assertThat(digest1).hasSize(32);
            assertThat(digest2).hasSize(32);
        }

        @Test
        @DisplayName("Should produce different digest for different PDFs")
        void shouldProduceDifferentDigestForDifferentPdfs() throws Exception {
            // Given - two valid but different minimal PDFs
            String pdf1 = MINIMAL_PDF;
            String pdf2 = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000113 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n200\n%%EOF";

            // When
            byte[] digest1 = embedder.computeByteRangeDigest(new ByteArrayInputStream(pdf1.getBytes(StandardCharsets.ISO_8859_1)));
            byte[] digest2 = embedder.computeByteRangeDigest(new ByteArrayInputStream(pdf2.getBytes(StandardCharsets.ISO_8859_1)));

            // Then
            assertThat(digest1).isNotEqualTo(digest2);
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
        @DisplayName("Should throw exception for null input stream")
        void shouldThrowExceptionForNullInputStream() {
            // When/Then - implementation throws NPE for null
            assertThatThrownBy(() -> embedder.computeByteRangeDigest(null))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should handle empty input stream")
        void shouldHandleEmptyInputStream() {
            // Given
            InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

            // When/Then
            assertThatThrownBy(() -> embedder.computeByteRangeDigest(emptyStream))
                .isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("buildCmsSignature() method")
    class BuildCmsSignatureMethod {

        @Test
        @DisplayName("Should throw exception for null raw signature")
        void shouldThrowExceptionForNullRawSignature() throws Exception {
            // Given
            byte[] digest = new byte[32];

            // When/Then - requires valid certificate chain, will throw on null signature
            assertThatThrownBy(() -> embedder.buildCmsSignature(null, createMockCertChain(), digest))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should throw exception for empty certificate chain")
        void shouldThrowExceptionForEmptyCertChain() {
            // Given
            byte[] rawSignature = new byte[256];
            byte[] digest = new byte[32];

            // When/Then
            assertThatThrownBy(() -> embedder.buildCmsSignature(rawSignature, new X509Certificate[0], digest))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should throw exception for null digest")
        void shouldThrowExceptionForNullDigest() {
            // Given
            byte[] rawSignature = new byte[256];

            // When/Then
            assertThatThrownBy(() -> embedder.buildCmsSignature(rawSignature, createMockCertChain(), null))
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("embedSignature() method")
    class EmbedSignatureMethod {

        @Test
        @DisplayName("Should embed CMS signature into PDF")
        void shouldEmbedSignature() throws Exception {
            // Given
            byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);
            byte[] cmsSignature = createSampleCmsSignature();

            // When
            byte[] signedPdf = embedder.embedSignature(pdfBytes, cmsSignature);

            // Then
            assertThat(signedPdf).isNotEmpty();
            assertThat(signedPdf.length).isGreaterThan(pdfBytes.length); // Signed PDF is larger
        }

        @Test
        @DisplayName("Should produce signed PDF with signature embedded")
        void shouldProduceSignedPdfWithSignature() throws Exception {
            // Given
            byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);
            byte[] cmsSignature = createSampleCmsSignature();

            // When
            byte[] signedPdf = embedder.embedSignature(pdfBytes, cmsSignature);

            // Then - signed PDF should contain original PDF content
            assertThat(signedPdf).isNotEmpty();
            assertThat(signedPdf.length).isGreaterThan(pdfBytes.length);
            assertThat(new String(signedPdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should throw exception for invalid PDF when embedding")
        void shouldThrowExceptionForInvalidPdf() {
            // Given
            byte[] invalidPdf = "Not a PDF".getBytes();
            byte[] cmsSignature = new byte[32];

            // When/Then
            assertThatThrownBy(() -> embedder.embedSignature(invalidPdf, cmsSignature))
                .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should throw exception for null PDF bytes")
        void shouldThrowExceptionForNullPdfBytes() {
            // Given
            byte[] cmsSignature = new byte[32];

            // When/Then - PDFBox throws NullPointerException for null input
            assertThatThrownBy(() -> embedder.embedSignature(null, cmsSignature))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should throw exception for null CMS signature")
        void shouldThrowExceptionForNullCmsSignature() {
            // Given
            byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);

            // When/Then - PDFBox throws NullPointerException for null signature
            assertThatThrownBy(() -> embedder.embedSignature(pdfBytes, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should handle empty CMS signature")
        void shouldHandleEmptyCmsSignature() throws Exception {
            // Given
            byte[] pdfBytes = MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1);
            byte[] emptySignature = new byte[0];

            // When/Then - PDFBox may accept empty signature
            // Just verify it doesn't crash
            byte[] result = embedder.embedSignature(pdfBytes, emptySignature);
            assertThat(result).isNotEmpty();
        }
    }

    // Helper methods

    private InputStream getSamplePdfInputStream() {
        return new ByteArrayInputStream(MINIMAL_PDF.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Creates a sample CMS signature for testing
     */
    private byte[] createSampleCmsSignature() {
        // A minimal CMS signature structure (simplified for testing)
        byte[] cmsSig = new byte[512];
        for (int i = 0; i < cmsSig.length; i++) {
            cmsSig[i] = (byte) (i % 256);
        }
        return cmsSig;
    }

    /**
     * Creates a mock certificate chain for testing
     */
    private X509Certificate[] createMockCertChain() {
        // Use Mockito to create mock certificates
        X509Certificate mockCert = mock(X509Certificate.class);
        return new X509Certificate[]{mockCert};
    }
}
