package com.wpanther.pdfsigning.infrastructure.client;

import com.wpanther.pdfsigning.domain.model.PadesLevel;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Implementation of PdfSigningService using deferred signing (signHash).
 *
 * This service:
 * 1. Downloads the unsigned PDF from the given URL
 * 2. Computes PDF byte range digest using PDFBox
 * 3. Authorizes the signing operation via CSC API
 * 4. Signs the hash using CSC signHash endpoint
 * 5. Constructs CMS/PKCS#7 signature locally
 * 6. Embeds signature into PDF using PDFBox
 * 7. Saves the signed PDF to the filesystem
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfSigningServiceImpl implements PdfSigningService {

    private final CSCAuthClient authClient;
    private final CSCApiClient apiClient;
    private final PadesSignatureEmbedder signatureEmbedder;
    private final CertificateParser certificateParser;
    private final CertificateValidator certificateValidator;
    private final SadTokenValidator sadTokenValidator;
    private final SignedPdfStorageProvider storageProvider;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.csc.client-id:#{null}}")
    private String clientId;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    @Value("${app.csc.hash-algo:SHA256}")
    private String hashAlgo;

    @Value("${app.pades.level:BASELINE_B}")
    private String padesLevelConfig;

    @Value("${app.pdf.max-size-bytes:104857600}") // 100MB default
    private long maxPdfSizeBytes;

    @Override
    public SignedPdfResult signPdf(String pdfUrl, String documentId) {
        // Generate request ID for distributed tracing
        String requestId = UUID.randomUUID().toString();
        try {
            log.info("Starting deferred PDF signing for document: {} (requestId: {})", documentId, requestId);

            // Step 1: Download PDF from URL (with size validation)
            log.debug("Downloading PDF from URL: {} (requestId: {})", pdfUrl, requestId);
            byte[] pdfContent = downloadPdfWithValidation(pdfUrl, requestId);
            log.debug("Downloaded PDF size: {} bytes (requestId: {})", pdfContent.length, requestId);

            // Step 2: Compute byte range digest using PDFBox
            log.debug("Computing PDF byte range digest");
            byte[] digest = signatureEmbedder.computeByteRangeDigest(
                new ByteArrayInputStream(pdfContent)
            );
            log.debug("Computed digest: {} bytes", digest.length);

            // Step 3: Authorize with CSC API
            log.debug("Authorizing signing operation with CSC API");
            // Use base64url encoding (URL-safe, no padding) for authorize request hash
            String base64urlDigest = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            CSCAuthorizeResponse authResponse = authClient.authorize(
                CSCAuthorizeRequest.builder()
                    .clientId(clientId)
                    .credentialID(credentialId)
                    .numSignatures("1")
                    .hashAlgo(hashAlgo)
                    .hash(new String[]{base64urlDigest})
                    .description("Thai e-Tax Invoice PDF Signing - " + documentId)
                    .build()
            );
            log.debug("Received SAD token from CSC API");

            // Step 3.5: Validate SAD token (security critical)
            sadTokenValidator.validate(authResponse, credentialId);
            log.debug("SAD token validated successfully");

            // Step 4: Call signHash endpoint
            // IMPORTANT: Use base64url encoding (URL-safe, no padding) for hash
            log.debug("Signing hash via CSC API");
            String base64urlHash = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

            CSCSignatureRequest signRequest = CSCSignatureRequest.builder()
                .clientId(clientId)
                .credentialID(credentialId)
                .SAD(authResponse.getSAD())
                .hashAlgo(hashAlgo)
                .signatureData(CSCSignatureRequest.SignatureData.builder()
                    .hashToSign(new String[]{base64urlHash})
                    .build())
                .build();

            CSCSignatureResponse signResponse = apiClient.signHash(signRequest);
            log.debug("Received signature from CSC API");

            // Step 5: Parse certificate chain
            log.debug("Parsing certificate chain");
            X509Certificate[] certChain = certificateParser.parseCertificateChain(
                signResponse.getCertificate()
            );
            log.debug("Parsed certificate chain: {} certificates", certChain.length);

            // Step 5.5: Validate certificate chain (security critical)
            certificateValidator.validateChain(certChain);
            log.debug("Certificate chain validated successfully");

            // Step 6: Build CMS/PKCS#7 signature
            log.debug("Building CMS/PKCS#7 signature");
            byte[] rawSignature = Base64.getDecoder().decode(signResponse.getSignatures()[0]);
            byte[] cmsSignature = signatureEmbedder.buildCmsSignature(
                rawSignature,
                certChain,
                digest
            );
            log.debug("Built CMS signature: {} bytes", cmsSignature.length);

            // Step 7: Embed signature into PDF
            log.debug("Embedding signature into PDF");
            byte[] signedPdf = signatureEmbedder.embedSignature(pdfContent, cmsSignature);
            log.debug("Embedded signature, signed PDF size: {} bytes", signedPdf.length);

            // Step 8: Store signed PDF
            SignedPdfStorageProvider.StorageResult storageResult = storageProvider.store(signedPdf, documentId);
            log.info("Stored signed PDF: path={}, url={}", storageResult.path(), storageResult.url());

            // Step 9: Build result with parsed PAdES level
            PadesLevel padesLevel = PadesLevel.fromCode("PAdES-" + padesLevelConfig);
            log.info("PDF signing completed with level: {} (requestId: {})", padesLevel.getCode(), requestId);

            return SignedPdfResult.builder()
                .signedPdfPath(storageResult.path())
                .signedPdfUrl(storageResult.url())
                .signedPdfSize((long) signedPdf.length)
                .transactionId(authResponse.getSAD()) // Use SAD as transaction ID
                .certificate(signResponse.getCertificate())
                .signatureLevel(padesLevel.getCode())
                .signatureTimestamp(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to sign PDF for document: {} (requestId: {})", documentId, requestId, e);
            throw new PdfSigningException("Failed to sign PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads PDF content from the given URL with size validation.
     *
     * @param pdfUrl URL to download from
     * @param requestId Request ID for tracing
     * @return PDF content bytes
     * @throws PdfSigningException if download fails or file is too large
     */
    byte[] downloadPdfWithValidation(String pdfUrl, String requestId) {
        try {
            // First, check content length if available (HEAD request)
            try {
                var headResponse = restTemplate.headForHeaders(pdfUrl);
                Long contentLength = headResponse.getContentLength();
                if (contentLength != null && contentLength > maxPdfSizeBytes) {
                    throw new PdfSigningException(
                        "PDF file exceeds maximum size: " + contentLength + " bytes (max: " + maxPdfSizeBytes + " bytes)"
                    );
                }
                log.debug("Content-Length header: {} bytes (requestId: {})", contentLength, requestId);
            } catch (Exception e) {
                log.debug("Could not check Content-Length header, proceeding with download: {}", e.getMessage());
            }

            // Download PDF
            byte[] content = restTemplate.getForObject(pdfUrl, byte[].class);

            // Validate downloaded size
            if (content == null) {
                throw new PdfSigningException("Received null content from URL: " + pdfUrl);
            }

            if (content.length > maxPdfSizeBytes) {
                throw new PdfSigningException(
                    "Downloaded PDF exceeds maximum size: " + content.length + " bytes (max: " + maxPdfSizeBytes + " bytes)"
                );
            }

            log.debug("Validated PDF size: {} bytes (requestId: {})", content.length, requestId);
            return content;

        } catch (PdfSigningException e) {
            throw e;
        } catch (Exception e) {
            throw new PdfSigningException("Failed to download PDF from URL: " + pdfUrl + " (requestId: " + requestId + ")", e);
        }
    }

}
