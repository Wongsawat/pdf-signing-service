package com.wpanther.pdfsigning.infrastructure.client;

import com.wpanther.pdfsigning.domain.service.PdfSigningService;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import com.wpanther.pdfsigning.infrastructure.pdf.CertificateParser;
import com.wpanther.pdfsigning.infrastructure.pdf.PadesSignatureEmbedder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;

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
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.csc.client-id:#{null}}")
    private String clientId;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    @Value("${app.csc.hash-algo:SHA256}")
    private String hashAlgo;

    @Value("${app.storage.base-path}")
    private String storagePath;

    @Value("${app.storage.base-url}")
    private String baseUrl;

    @Value("${app.pades.level:BASELINE_B}")
    private String padesLevel;

    @Override
    public SignedPdfResult signPdf(String pdfUrl, String documentId) {
        try {
            log.info("Starting deferred PDF signing for document: {}", documentId);

            // Step 1: Download PDF from URL
            log.debug("Downloading PDF from URL: {}", pdfUrl);
            byte[] pdfContent = downloadPdf(pdfUrl);
            log.debug("Downloaded PDF size: {} bytes", pdfContent.length);

            // Step 2: Compute byte range digest using PDFBox
            log.debug("Computing PDF byte range digest");
            byte[] digest = signatureEmbedder.computeByteRangeDigest(
                new ByteArrayInputStream(pdfContent)
            );
            log.debug("Computed digest: {} bytes", digest.length);

            // Step 3: Authorize with CSC API
            log.debug("Authorizing signing operation with CSC API");
            CSCAuthorizeResponse authResponse = authClient.authorize(
                CSCAuthorizeRequest.builder()
                    .credentialID(credentialId)
                    .numSignatures(1)
                    .hash(new String[]{Base64.getEncoder().encodeToString(digest)})
                    .description("Thai e-Tax Invoice PDF Signing - " + documentId)
                    .build()
            );
            log.debug("Received SAD token from CSC API");

            // Step 4: Call signHash endpoint
            // IMPORTANT: Use base64url encoding (URL-safe, no padding) for hash
            log.debug("Signing hash via CSC API");
            String base64urlHash = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

            CSCSignatureRequest signRequest = CSCSignatureRequest.builder()
                .clientId(clientId)
                .credentialID(credentialId)
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

            // Step 8: Save to filesystem
            String filePath = saveSignedPdf(signedPdf, documentId);
            String fileUrl = generateFileUrl(filePath);
            log.info("Saved signed PDF to: {}", filePath);

            // Step 9: Build result
            return SignedPdfResult.builder()
                .signedPdfPath(filePath)
                .signedPdfUrl(fileUrl)
                .signedPdfSize((long) signedPdf.length)
                .transactionId(authResponse.getSAD()) // Use SAD as transaction ID
                .certificate(signResponse.getCertificate())
                .signatureLevel("PAdES-" + padesLevel)
                .signatureTimestamp(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to sign PDF for document: {}", documentId, e);
            throw new PdfSigningException("Failed to sign PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads PDF content from the given URL.
     */
    private byte[] downloadPdf(String pdfUrl) {
        try {
            return restTemplate.getForObject(pdfUrl, byte[].class);
        } catch (Exception e) {
            throw new PdfSigningException("Failed to download PDF from URL: " + pdfUrl, e);
        }
    }

    /**
     * Saves the signed PDF to the filesystem.
     *
     * File structure: {storagePath}/YYYY/MM/DD/signed-pdf-{documentId}.pdf
     */
    private String saveSignedPdf(byte[] signedPdf, String documentId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String year = String.format("%04d", now.getYear());
            String month = String.format("%02d", now.getMonthValue());
            String day = String.format("%02d", now.getDayOfMonth());

            Path directory = Paths.get(storagePath, year, month, day);
            Files.createDirectories(directory);

            String filename = String.format("signed-pdf-%s.pdf", documentId);
            Path filePath = directory.resolve(filename);

            Files.write(filePath, signedPdf);

            return filePath.toString();
        } catch (Exception e) {
            throw new PdfSigningException("Failed to save signed PDF to filesystem", e);
        }
    }

    /**
     * Generates the public URL for the signed PDF.
     */
    private String generateFileUrl(String filePath) {
        String relativePath = filePath.substring(storagePath.length());
        return baseUrl + "/signed-documents" + relativePath.replace("\\", "/");
    }
}
