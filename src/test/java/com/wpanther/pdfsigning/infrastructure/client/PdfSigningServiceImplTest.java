package com.wpanther.pdfsigning.infrastructure.client;

import com.wpanther.pdfsigning.domain.service.PdfSigningService;
import com.wpanther.pdfsigning.domain.service.SignedPdfStorageProvider;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.SadTokenValidator;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import com.wpanther.pdfsigning.infrastructure.pdf.CertificateParser;
import com.wpanther.pdfsigning.infrastructure.pdf.CertificateValidator;
import com.wpanther.pdfsigning.infrastructure.pdf.PadesSignatureEmbedder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PdfSigningServiceImpl.
 *
 * Tests the complete PDF signing flow including:
 * - PDF download with size validation
 * - CSC API authorization and signing
 * - Certificate parsing and validation
 * - CMS signature construction
 * - Signature embedding
 * - Storage operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdfSigningServiceImpl Tests")
class PdfSigningServiceImplTest {

    @Mock
    private CSCAuthClient authClient;

    @Mock
    private CSCApiClient apiClient;

    @Mock
    private PadesSignatureEmbedder signatureEmbedder;

    @Mock
    private CertificateParser certificateParser;

    @Mock
    private CertificateValidator certificateValidator;

    @Mock
    private SadTokenValidator sadTokenValidator;

    @Mock
    private SignedPdfStorageProvider storageProvider;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PdfSigningServiceImpl service;

    private static final String TEST_PDF_URL = "http://example.com/test.pdf";
    private static final String TEST_DOCUMENT_ID = "test-doc-123";
    private static final String TEST_SAD_TOKEN = "test-sad-token-xyz";
    private static final byte[] TEST_PDF_CONTENT = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] TEST_DIGEST = new byte[32]; // SHA-256 digest
    private static final byte[] TEST_RAW_SIGNATURE = "raw-signature-bytes".getBytes();
    private static final byte[] TEST_CMS_SIGNATURE = "cms-signature-bytes".getBytes();
    private static final byte[] TEST_SIGNED_PDF = "signed-pdf-content".getBytes();

    @BeforeEach
    void setUp() {
        // Set default configuration values
        ReflectionTestUtils.setField(service, "clientId", "pdf-signing-service");
        ReflectionTestUtils.setField(service, "credentialId", "test-credential");
        ReflectionTestUtils.setField(service, "hashAlgo", "SHA256");
        // Note: Service prepends "PAdES-" so "BASELINE-B" becomes "PAdES-BASELINE-B"
        ReflectionTestUtils.setField(service, "padesLevelConfig", "BASELINE-B");
        ReflectionTestUtils.setField(service, "maxPdfSizeBytes", 104857600L); // 100MB
        // Inject mock RestTemplate (since service creates its own instance)
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    @Nested
    @DisplayName("signPdf() - Happy Path")
    class SignPdfHappyPath {

        @Test
        @DisplayName("Should successfully sign a PDF through complete flow")
        void shouldSuccessfullySignPdf() throws Exception {
            // Given - PDF download setup
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentLength((long) TEST_PDF_CONTENT.length);
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(httpHeaders);
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(TEST_PDF_CONTENT);

            // Given - PDF digest computation
            when(signatureEmbedder.computeByteRangeDigest(any(ByteArrayInputStream.class)))
                .thenReturn(TEST_DIGEST);

            // Given - CSC Authorization
            CSCAuthorizeResponse authResponse = new CSCAuthorizeResponse(TEST_SAD_TOKEN, 300L);
            when(authClient.authorize(any(CSCAuthorizeRequest.class))).thenReturn(authResponse);

            // Given - SAD token validation (do nothing)
            doNothing().when(sadTokenValidator).validate(any(), any());

            // Given - CSC Signing
            CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
                .signatureAlgorithm("1.2.840.113549.1.1.11") // SHA256withRSA
                .signatures(new String[]{Base64.getEncoder().encodeToString(TEST_RAW_SIGNATURE)})
                .certificate(getSampleCertificatePem())
                .build();
            when(apiClient.signHash(any(CSCSignatureRequest.class))).thenReturn(signResponse);

            // Given - Certificate parsing
            X509Certificate[] certChain = createMockCertificateChain();
            when(certificateParser.parseCertificateChain(anyString())).thenReturn(certChain);

            // Given - Certificate validation (do nothing)
            doNothing().when(certificateValidator).validateChain(any());

            // Given - CMS signature building
            when(signatureEmbedder.buildCmsSignature(eq(TEST_RAW_SIGNATURE), any(), eq(TEST_DIGEST)))
                .thenReturn(TEST_CMS_SIGNATURE);

            // Given - Signature embedding
            when(signatureEmbedder.embedSignature(eq(TEST_PDF_CONTENT), eq(TEST_CMS_SIGNATURE)))
                .thenReturn(TEST_SIGNED_PDF);

            // Given - Storage
            SignedPdfStorageProvider.StorageResult storageResult = new SignedPdfStorageProvider.StorageResult(
                "/storage/signed/test-doc-123.pdf",
                "http://example.com/signed/test-doc-123.pdf"
            );
            when(storageProvider.store(eq(TEST_SIGNED_PDF), eq(TEST_DOCUMENT_ID))).thenReturn(storageResult);

            // When
            PdfSigningService.SignedPdfResult result = service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID);

            // Then - Verify result
            assertThat(result.getSignedPdfPath()).isEqualTo("/storage/signed/test-doc-123.pdf");
            assertThat(result.getSignedPdfUrl()).isEqualTo("http://example.com/signed/test-doc-123.pdf");
            assertThat(result.getSignedPdfSize()).isEqualTo((long) TEST_SIGNED_PDF.length);
            assertThat(result.getTransactionId()).isEqualTo(TEST_SAD_TOKEN);
            assertThat(result.getCertificate()).isEqualTo(getSampleCertificatePem());
            assertThat(result.getSignatureLevel()).isEqualTo("PAdES-BASELINE-B");
            assertThat(result.getSignatureTimestamp()).isNotNull();
            assertThat(result.getSignatureTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());

            // Then - Verify invocation order
            verify(restTemplate).headForHeaders(TEST_PDF_URL);
            verify(restTemplate).getForObject(TEST_PDF_URL, byte[].class);
            verify(signatureEmbedder).computeByteRangeDigest(any(ByteArrayInputStream.class));
            verify(authClient).authorize(any(CSCAuthorizeRequest.class));
            verify(sadTokenValidator).validate(authResponse, "test-credential");
            verify(apiClient).signHash(any(CSCSignatureRequest.class));
            verify(certificateParser).parseCertificateChain(getSampleCertificatePem());
            verify(certificateValidator).validateChain(certChain);
            verify(signatureEmbedder).buildCmsSignature(eq(TEST_RAW_SIGNATURE), any(), eq(TEST_DIGEST));
            verify(signatureEmbedder).embedSignature(eq(TEST_PDF_CONTENT), eq(TEST_CMS_SIGNATURE));
            verify(storageProvider).store(eq(TEST_SIGNED_PDF), eq(TEST_DOCUMENT_ID));
        }

        @Test
        @DisplayName("Should use base64url encoding for CSC hash")
        void shouldUseBase64UrlEncoding() throws Exception {
            // Given
            setupSuccessfulSigningFlow();

            // When
            service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID);

            // Then - Capture authorize request and verify base64url encoding
            ArgumentCaptor<CSCAuthorizeRequest> authCaptor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
            verify(authClient).authorize(authCaptor.capture());

            CSCAuthorizeRequest authRequest = authCaptor.getValue();
            assertThat(authRequest.getHash()).isNotEmpty();
            // base64url should not have padding (=)
            assertThat(authRequest.getHash()[0]).doesNotEndWith("=");
            // base64url uses - and _ instead of + and /
            assertThat(authRequest.getHash()[0]).doesNotContain("+");
            assertThat(authRequest.getHash()[0]).doesNotContain("/");

            // Then - Capture signHash request and verify base64url encoding
            ArgumentCaptor<CSCSignatureRequest> signCaptor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
            verify(apiClient).signHash(signCaptor.capture());

            CSCSignatureRequest signRequest = signCaptor.getValue();
            assertThat(signRequest.getSignatureData().getHashToSign()).isNotEmpty();
            assertThat(signRequest.getSignatureData().getHashToSign()[0]).doesNotEndWith("=");
        }
    }

    @Nested
    @DisplayName("signPdf() - Error Handling")
    class SignPdfErrorHandling {

        @Test
        @DisplayName("Should throw exception when PDF download returns null")
        void shouldThrowExceptionWhenPdfDownloadReturnsNull() {
            // Given
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasMessageContaining("null content");
        }

        @Test
        @DisplayName("Should throw exception when downloaded PDF exceeds maximum size")
        void shouldThrowExceptionWhenDownloadedPdfExceedsMaxSize() {
            // Given
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
            byte[] largePdf = new byte[200_000_000]; // 200MB
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(largePdf);

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasMessageContaining("exceeds maximum size");
        }

        @Test
        @DisplayName("Should throw exception when CSC authorization fails")
        void shouldThrowExceptionWhenAuthorizationFails() throws Exception {
            // Given - PDF download succeeds
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(TEST_PDF_CONTENT);
            when(signatureEmbedder.computeByteRangeDigest(any(ByteArrayInputStream.class)))
                .thenReturn(TEST_DIGEST);

            // Given - Authorization fails
            when(authClient.authorize(any(CSCAuthorizeRequest.class)))
                .thenThrow(new RuntimeException("CSC service unavailable"));

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasMessageContaining("Failed to sign PDF")
                .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should throw exception when SAD token validation fails")
        void shouldThrowExceptionWhenSadValidationFails() throws Exception {
            // Given - Setup up to authorization
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(TEST_PDF_CONTENT);
            when(signatureEmbedder.computeByteRangeDigest(any(ByteArrayInputStream.class)))
                .thenReturn(TEST_DIGEST);

            CSCAuthorizeResponse authResponse = new CSCAuthorizeResponse(TEST_SAD_TOKEN, 10L); // Too short
            when(authClient.authorize(any(CSCAuthorizeRequest.class))).thenReturn(authResponse);

            // Given - SAD validation fails
            doThrow(new SadTokenValidator.SadTokenValidationException("SAD token expires too soon"))
                .when(sadTokenValidator).validate(any(), any());

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasMessageContaining("Failed to sign PDF");
        }

        @Test
        @DisplayName("Should throw exception when CSC signHash fails")
        void shouldThrowExceptionWhenSignHashFails() throws Exception {
            // Given - Setup up to signHash
            setupSigningUpToSignHash();

            // Given - signHash fails
            when(apiClient.signHash(any(CSCSignatureRequest.class)))
                .thenThrow(new RuntimeException("CSC signing error"));

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class);
        }

        @Test
        @DisplayName("Should throw exception when certificate parsing fails")
        void shouldThrowExceptionWhenCertificateParsingFails() throws Exception {
            // Given - Setup up to certificate parsing
            setupSigningUpToCertificateParsing();

            // Given - Certificate parsing fails
            when(certificateParser.parseCertificateChain(anyString()))
                .thenThrow(new IOException("Invalid PEM format"));

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasMessageContaining("Failed to sign PDF");
        }

        @Test
        @DisplayName("Should throw exception when certificate validation fails")
        void shouldThrowExceptionWhenCertificateValidationFails() throws Exception {
            // Given - Setup up to certificate validation
            setupSigningUpToCertificateValidation();

            // Given - Certificate validation fails
            doThrow(new CertificateValidator.CertificateValidationException("Certificate expired"))
                .when(certificateValidator).validateChain(any());

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class);
        }

        @Test
        @DisplayName("Should throw exception when storage fails")
        void shouldThrowExceptionWhenStorageFails() throws Exception {
            // Given - Setup complete signing flow
            setupSigningUpToStorage();

            // Given - Storage fails
            when(storageProvider.store(any(byte[].class), anyString()))
                .thenThrow(new SignedPdfStorageProvider.StorageException("Storage unavailable", null));

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class);
        }

        @Test
        @DisplayName("Should wrap network exceptions in PdfSigningException")
        void shouldWrapNetworkExceptionsInPdfSigningException() throws Exception {
            // Given
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class))
                .thenThrow(new RuntimeException("Network error"));

            // When/Then
            assertThatThrownBy(() -> service.signPdf(TEST_PDF_URL, TEST_DOCUMENT_ID))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("downloadPdfWithValidation() - PDF Download")
    class DownloadPdfWithValidation {

        @Test
        @DisplayName("Should download PDF within size limit")
        void shouldDownloadPdfWithinSizeLimit() {
            // Given
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentLength((long) TEST_PDF_CONTENT.length);
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(httpHeaders);
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(TEST_PDF_CONTENT);

            // When
            byte[] result = service.downloadPdfWithValidation(TEST_PDF_URL, "test-request-id");

            // Then
            assertThat(result).isEqualTo(TEST_PDF_CONTENT);
            verify(restTemplate).headForHeaders(TEST_PDF_URL);
            verify(restTemplate).getForObject(TEST_PDF_URL, byte[].class);
        }

        @Test
        @DisplayName("Should skip Content-Length check when header not available")
        void shouldSkipContentLengthCheckWhenHeaderNotAvailable() {
            // Given
            when(restTemplate.headForHeaders(TEST_PDF_URL))
                .thenThrow(new RuntimeException("HEAD not supported"));
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(TEST_PDF_CONTENT);

            // When
            byte[] result = service.downloadPdfWithValidation(TEST_PDF_URL, "test-request-id");

            // Then
            assertThat(result).isEqualTo(TEST_PDF_CONTENT);
            verify(restTemplate).getForObject(TEST_PDF_URL, byte[].class);
        }

        @Test
        @DisplayName("Should reject downloaded PDF exceeding size limit")
        void shouldRejectDownloadedPdfExceedingSizeLimit() {
            // Given
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
            byte[] largePdf = new byte[200_000_000];
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(largePdf);

            // When/Then
            assertThatThrownBy(() -> service.downloadPdfWithValidation(TEST_PDF_URL, "test-request-id"))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasMessageContaining("exceeds maximum size");
        }

        @Test
        @DisplayName("Should reject null content from download")
        void shouldRejectNullContentFromDownload() {
            // Given
            when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
            when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> service.downloadPdfWithValidation(TEST_PDF_URL, "test-request-id"))
                .isInstanceOf(PdfSigningService.PdfSigningException.class)
                .hasMessageContaining("null content");
        }
    }

    // Helper methods

    private String getSampleCertificatePem() {
        return "-----BEGIN CERTIFICATE-----\n" +
            "MIIBkTCB+wIJAKHHCgVZU1B2MA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMMBnRl\n" +
            "c3RDQTAeFw0yNDAxMDEwMDAwMDBaFw0yNTAxMDEwMDAwMDBaMBExDzANBgNVBAMMBnRl\n" +
            "c3RDQTCBnzANBgkqhkiGw0BAQEFAAOBjQAwgYkCgYEAwT8kqCEm4Y5lqZ5vZ2JhY2t1\n" +
            "YmUxMjM0NTY3ODkwMDEwMjAzMDQwNTA2MDcwODA5MTAxMTEyMTMxNDE1MTYxNzE4MTky\n" +
            "MjEyMjIyMjQyNTI2MjcyODI5MzAzMTMyMzMzNDM1MzYzNzM4Mzk0MDQxNDI0MzQ0NDQ1\n" +
            "NDY0NzQ4NDk1MDUxNTI1MzU0NTU1NjU3NTg1OTYwNjM2MjCAwEAATANBgkqhkiGw0BAQs\n" +
            "FAAOBgQBA7X8kKVJ8jf8JlJLzJ8qGpN7jK8N9J0K8M8L8P8Q8R8S8T8U8V8W8X8Y8Z8\n" +
            "a8b8c8d8e8f8g8h8i8j8k8l8m8n8o8p8q8r8s8t8u8v8w8x8y8z8==\n" +
            "-----END CERTIFICATE-----";
    }

    private X509Certificate[] createMockCertificateChain() {
        X509Certificate cert1 = mock(X509Certificate.class);
        X509Certificate cert2 = mock(X509Certificate.class);
        return new X509Certificate[]{cert1, cert2};
    }

    private void setupSuccessfulSigningFlow() throws Exception {
        // Complete setup for successful signing flow
        when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
        when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(TEST_PDF_CONTENT);
        when(signatureEmbedder.computeByteRangeDigest(any(ByteArrayInputStream.class))).thenReturn(TEST_DIGEST);

        CSCAuthorizeResponse authResponse = new CSCAuthorizeResponse(TEST_SAD_TOKEN, 300L);
        when(authClient.authorize(any(CSCAuthorizeRequest.class))).thenReturn(authResponse);
        doNothing().when(sadTokenValidator).validate(any(), any());

        CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
            .signatureAlgorithm("1.2.840.113549.1.1.11")
            .signatures(new String[]{Base64.getEncoder().encodeToString(TEST_RAW_SIGNATURE)})
            .certificate(getSampleCertificatePem())
            .build();
        when(apiClient.signHash(any(CSCSignatureRequest.class))).thenReturn(signResponse);

        X509Certificate[] certChain = createMockCertificateChain();
        when(certificateParser.parseCertificateChain(anyString())).thenReturn(certChain);
        doNothing().when(certificateValidator).validateChain(any());

        when(signatureEmbedder.buildCmsSignature(eq(TEST_RAW_SIGNATURE), any(), eq(TEST_DIGEST)))
            .thenReturn(TEST_CMS_SIGNATURE);
        when(signatureEmbedder.embedSignature(eq(TEST_PDF_CONTENT), eq(TEST_CMS_SIGNATURE)))
            .thenReturn(TEST_SIGNED_PDF);

        SignedPdfStorageProvider.StorageResult storageResult = new SignedPdfStorageProvider.StorageResult(
            "/storage/signed/test-doc-123.pdf",
            "http://example.com/signed/test-doc-123.pdf"
        );
        when(storageProvider.store(eq(TEST_SIGNED_PDF), eq(TEST_DOCUMENT_ID))).thenReturn(storageResult);
    }

    private void setupSigningUpToSignHash() throws Exception {
        when(restTemplate.headForHeaders(TEST_PDF_URL)).thenReturn(new HttpHeaders());
        when(restTemplate.getForObject(TEST_PDF_URL, byte[].class)).thenReturn(TEST_PDF_CONTENT);
        when(signatureEmbedder.computeByteRangeDigest(any(ByteArrayInputStream.class))).thenReturn(TEST_DIGEST);

        CSCAuthorizeResponse authResponse = new CSCAuthorizeResponse(TEST_SAD_TOKEN, 300L);
        when(authClient.authorize(any(CSCAuthorizeRequest.class))).thenReturn(authResponse);
        doNothing().when(sadTokenValidator).validate(any(), any());
    }

    private void setupSigningUpToCertificateParsing() throws Exception {
        setupSigningUpToSignHash();

        CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
            .signatureAlgorithm("1.2.840.113549.1.1.11")
            .signatures(new String[]{Base64.getEncoder().encodeToString(TEST_RAW_SIGNATURE)})
            .certificate(getSampleCertificatePem())
            .build();
        when(apiClient.signHash(any(CSCSignatureRequest.class))).thenReturn(signResponse);
    }

    private void setupSigningUpToCertificateValidation() throws Exception {
        setupSigningUpToCertificateParsing();

        X509Certificate[] certChain = createMockCertificateChain();
        when(certificateParser.parseCertificateChain(anyString())).thenReturn(certChain);
    }

    private void setupSigningUpToStorage() throws Exception {
        setupSigningUpToCertificateValidation();

        doNothing().when(certificateValidator).validateChain(any());

        when(signatureEmbedder.buildCmsSignature(eq(TEST_RAW_SIGNATURE), any(), eq(TEST_DIGEST)))
            .thenReturn(TEST_CMS_SIGNATURE);
        when(signatureEmbedder.embedSignature(eq(TEST_PDF_CONTENT), eq(TEST_CMS_SIGNATURE)))
            .thenReturn(TEST_SIGNED_PDF);
    }
}
