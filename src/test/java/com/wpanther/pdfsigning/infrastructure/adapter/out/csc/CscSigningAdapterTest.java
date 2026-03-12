package com.wpanther.pdfsigning.infrastructure.adapter.out.csc;

import com.wpanther.pdfsigning.domain.model.SigningException;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCAuthClient;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.SadTokenValidator;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCSignatureRequest;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCSignatureResponse;
import com.wpanther.pdfsigning.infrastructure.config.properties.CscProperties;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.CertificateParser;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.CertificateValidator;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.PadesSignatureEmbedder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CscSigningAdapter}.
 * <p>
 * Tests the CSC signing adapter using mocked CSC clients and dependencies.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CscSigningAdapter Tests")
class CscSigningAdapterTest {

    @Mock
    private CSCAuthClient mockAuthClient;

    @Mock
    private CSCApiClient mockApiClient;

    @Mock
    private PadesSignatureEmbedder mockSignatureEmbedder;

    @Mock
    private CertificateParser mockCertificateParser;

    @Mock
    private CertificateValidator mockCertificateValidator;

    @Mock
    private SadTokenValidator mockSadTokenValidator;

    @Mock
    private CscProperties mockCscProperties;

    private CscSigningAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CscSigningAdapter(
            mockAuthClient,
            mockApiClient,
            mockSignatureEmbedder,
            mockCertificateParser,
            mockCertificateValidator,
            mockSadTokenValidator,
            mockCscProperties
        );
        // Set up mock properties defaults
        lenient().when(mockCscProperties.getClientId()).thenReturn("test-client");
        lenient().when(mockCscProperties.getCredentialId()).thenReturn("test-credential");
        lenient().when(mockCscProperties.getHashAlgo()).thenReturn("SHA256");
    }

    @Nested
    @DisplayName("signPdf() method")
    class SignPdfMethod {

        @Test
        @DisplayName("Should sign PDF successfully through complete flow")
        void shouldSignPdfSuccessfully() throws Exception {
            // Given
            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] digest = new byte[32];
            X509Certificate[] certChain = new X509Certificate[0];

            CSCAuthorizeResponse authResponse = new CSCAuthorizeResponse();
            authResponse.setSAD("test-sad-token");

            CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{Base64.getEncoder().encodeToString("raw-signature".getBytes())})
                .certificate("-----BEGIN CERTIFICATE-----\nMIIC9...\n-----END CERTIFICATE-----")
                .build();

            byte[] cmsSignature = "cms-signature".getBytes();
            byte[] signedPdf = "signed-pdf-content".getBytes();

            when(mockAuthClient.authorize(any())).thenReturn(authResponse);
            when(mockApiClient.signHash(any())).thenReturn(signResponse);
            when(mockCertificateParser.parseCertificateChain(any())).thenReturn(certChain);
            when(mockSignatureEmbedder.buildCmsSignature(any(), any(), any())).thenReturn(cmsSignature);
            when(mockSignatureEmbedder.embedSignature(any(), any())).thenReturn(signedPdf);

            // When
            byte[] result = adapter.signPdf(pdfBytes, digest, certChain);

            // Then
            assertThat(result).isEqualTo(signedPdf);
            verify(mockAuthClient).authorize(any());
            verify(mockSadTokenValidator).validate(authResponse, "test-credential");
            verify(mockApiClient).signHash(any());
            verify(mockCertificateParser).parseCertificateChain(any());
            verify(mockSignatureEmbedder).buildCmsSignature(any(), any(), any());
            verify(mockSignatureEmbedder).embedSignature(pdfBytes, cmsSignature);
        }

        @Test
        @DisplayName("Should propagate authorization exceptions as SigningException")
        void shouldPropagateAuthException() {
            // Given
            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] digest = new byte[32];
            X509Certificate[] certChain = new X509Certificate[0];

            when(mockAuthClient.authorize(any()))
                .thenThrow(new RuntimeException("Auth failed"));

            // When/Then
            assertThatThrownBy(() -> adapter.signPdf(pdfBytes, digest, certChain))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Failed to sign PDF");
        }

        @Test
        @DisplayName("Should propagate signing exceptions as SigningException")
        void shouldPropagateSigningException() throws Exception {
            // Given
            byte[] pdfBytes = "test pdf content".getBytes();
            byte[] digest = new byte[32];
            X509Certificate[] certChain = new X509Certificate[0];

            CSCAuthorizeResponse authResponse = new CSCAuthorizeResponse();
            authResponse.setSAD("test-sad-token");

            when(mockAuthClient.authorize(any())).thenReturn(authResponse);
            when(mockApiClient.signHash(any()))
                .thenThrow(new RuntimeException("Signing failed"));

            // When/Then
            assertThatThrownBy(() -> adapter.signPdf(pdfBytes, digest, certChain))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Failed to sign PDF");
        }
    }

    @Nested
    @DisplayName("validateCertificateChain() method")
    class ValidateCertificateChainMethod {

        @Test
        @DisplayName("Should validate certificate chain successfully")
        void shouldValidateCertificateChain() throws Exception {
            // Given
            X509Certificate[] certChain = new X509Certificate[0];
            doNothing().when(mockCertificateValidator).validateChain(certChain);

            // When/Then - should not throw
            adapter.validateCertificateChain(certChain);

            verify(mockCertificateValidator).validateChain(certChain);
        }

        @Test
        @DisplayName("Should propagate validation exceptions as SigningException")
        void shouldPropagateValidationException() throws Exception {
            // Given
            X509Certificate[] certChain = new X509Certificate[0];
            doThrow(new RuntimeException("Validation failed"))
                .when(mockCertificateValidator).validateChain(certChain);

            // When/Then
            assertThatThrownBy(() -> adapter.validateCertificateChain(certChain))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("Certificate validation failed");
        }
    }
}
