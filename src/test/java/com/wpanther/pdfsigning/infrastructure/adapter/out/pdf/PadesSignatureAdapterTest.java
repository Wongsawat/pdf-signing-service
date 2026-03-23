package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import com.wpanther.pdfsigning.domain.model.SigningException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PadesSignatureAdapter}.
 * <p>
 * Tests the PDF adapter using mocked {@link PadesDigestComputer} and {@link PadesEmbedder}.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PadesSignatureAdapter Tests")
class PadesSignatureAdapterTest {

    @Mock
    private PadesDigestComputer mockDigestComputer;

    @Mock
    private PadesEmbedder mockPdfEmbedder;

    private PadesSignatureAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PadesSignatureAdapter(mockDigestComputer, mockPdfEmbedder);
    }

    @Nested
    @DisplayName("computeByteRangeDigest() method")
    class ComputeByteRangeDigestMethod {

        @Test
        @DisplayName("Should compute digest successfully")
        void shouldComputeDigest() throws Exception {
            // Given
            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] expectedDigest = new byte[32];
            when(mockDigestComputer.computeByteRangeDigest(any(InputStream.class)))
                .thenReturn(expectedDigest);

            // When
            byte[] result = adapter.computeByteRangeDigest(pdfBytes);

            // Then
            assertThat(result).isEqualTo(expectedDigest);
            verify(mockDigestComputer).computeByteRangeDigest(any(InputStream.class));
        }

        @Test
        @DisplayName("Should wrap IOException as SigningException")
        void shouldPropagateException() throws Exception {
            // Given
            byte[] pdfBytes = "test pdf content".getBytes();
            when(mockDigestComputer.computeByteRangeDigest(any(InputStream.class)))
                .thenThrow(new IOException("PDF read error"));

            // When/Then
            assertThatThrownBy(() -> adapter.computeByteRangeDigest(pdfBytes))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Failed to compute PDF digest");
        }
    }

    @Nested
    @DisplayName("embedSignature() method")
    class EmbedSignatureMethod {

        @Test
        @DisplayName("Should embed signature successfully")
        void shouldEmbedSignature() throws Exception {
            // Given
            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] cmsSignature = "cms signature".getBytes();
            byte[] signedPdf = "signed pdf content".getBytes();
            when(mockPdfEmbedder.embedSignature(pdfBytes, cmsSignature))
                .thenReturn(signedPdf);

            // When
            byte[] result = adapter.embedSignature(pdfBytes, cmsSignature);

            // Then
            assertThat(result).isEqualTo(signedPdf);
            verify(mockPdfEmbedder).embedSignature(pdfBytes, cmsSignature);
        }

        @Test
        @DisplayName("Should wrap IOException as SigningException")
        void shouldPropagateException() throws Exception {
            // Given
            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] cmsSignature = "cms signature".getBytes();
            when(mockPdfEmbedder.embedSignature(pdfBytes, cmsSignature))
                .thenThrow(new IOException("PDF write error"));

            // When/Then
            assertThatThrownBy(() -> adapter.embedSignature(pdfBytes, cmsSignature))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Failed to embed signature");
        }
    }
}
