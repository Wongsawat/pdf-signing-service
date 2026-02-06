package com.wpanther.pdfsigning.infrastructure.client;

import com.wpanther.pdfsigning.domain.service.PdfSigningService;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignDocumentRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignDocumentResponse;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.PadesSignatureAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Implementation of PdfSigningService using CSC API v2.0.
 *
 * This service:
 * 1. Downloads the unsigned PDF from the given URL
 * 2. Authorizes the signing operation via CSC API
 * 3. Signs the PDF using PAdES-BASELINE-T format
 * 4. Saves the signed PDF to the filesystem
 * 5. Returns the signed PDF metadata
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfSigningServiceImpl implements PdfSigningService {

    private final CSCAuthClient authClient;
    private final CSCApiClient apiClient;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.csc.credential-id}")
    private String credentialId;

    @Value("${app.csc.signature-level}")
    private String signatureLevel;

    @Value("${app.csc.signature-form}")
    private String signatureForm;

    @Value("${app.csc.digest-algorithm}")
    private String digestAlgorithm;

    @Value("${app.csc.reason}")
    private String reason;

    @Value("${app.csc.location}")
    private String location;

    @Value("${app.csc.contact-info:}")
    private String contactInfo;

    @Value("${app.storage.base-path}")
    private String storagePath;

    @Value("${app.storage.base-url}")
    private String baseUrl;

    @Override
    public SignedPdfResult signPdf(String pdfUrl, String documentId) {
        try {
            log.info("Starting PDF signing process for document: {}", documentId);

            // 1. Download PDF from URL
            log.debug("Downloading PDF from URL: {}", pdfUrl);
            byte[] pdfContent = downloadPdf(pdfUrl);
            log.debug("Downloaded PDF size: {} bytes", pdfContent.length);

            // 2. Calculate document digest
            String documentDigest = calculateDigest(pdfContent);
            log.debug("Calculated document digest");

            // 3. Authorize and get SAD token
            log.debug("Authorizing signing operation with CSC API");
            CSCAuthorizeResponse authResponse = authClient.authorize(
                    CSCAuthorizeRequest.builder()
                            .credentialID(credentialId)
                            .numSignatures(1)
                            .hash(new String[]{documentDigest})
                            .description("Thai e-Tax Invoice PDF Signing - " + documentId)
                            .build()
            );
            log.debug("Received SAD token from CSC API");

            // 4. Build PAdES signature attributes
            PadesSignatureAttributes attributes = PadesSignatureAttributes.builder()
                    .signatureType("PAdES")
                    .signatureLevel(signatureLevel)
                    .signatureForm(signatureForm)
                    .digestAlgorithm(digestAlgorithm)
                    .reason(reason)
                    .location(location)
                    .contactInfo(contactInfo)
                    .build();

            // 5. Sign the PDF via CSC API
            log.debug("Signing PDF via CSC API");
            CSCSignDocumentResponse signResponse = apiClient.signDocument(
                    CSCSignDocumentRequest.builder()
                            .credentialID(credentialId)
                            .SAD(authResponse.getSAD())
                            .document(Base64.getEncoder().encodeToString(pdfContent))
                            .documentDigest(documentDigest)
                            .signatureAttributes(attributes)
                            .returnSignedDocument(true)
                            .build()
            );
            log.debug("Received signed PDF from CSC API, transaction ID: {}", signResponse.getTransactionID());

            // 6. Decode signed PDF
            byte[] signedPdf = Base64.getDecoder().decode(signResponse.getSignedDocument());
            log.debug("Decoded signed PDF size: {} bytes", signedPdf.length);

            // 7. Save to filesystem
            String filePath = saveSignedPdf(signedPdf, documentId);
            String fileUrl = generateFileUrl(filePath);
            log.info("Saved signed PDF to: {}", filePath);

            // 8. Parse timestamp
            LocalDateTime signatureTimestamp = parseTimestamp(signResponse.getTimestamp());

            // 9. Build and return result
            return SignedPdfResult.builder()
                    .signedPdfPath(filePath)
                    .signedPdfUrl(fileUrl)
                    .signedPdfSize((long) signedPdf.length)
                    .transactionId(signResponse.getTransactionID())
                    .certificate(signResponse.getCertificate())
                    .signatureLevel(signatureLevel)
                    .signatureTimestamp(signatureTimestamp)
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
     * Calculates the SHA-256 digest of the PDF content.
     */
    private String calculateDigest(byte[] pdfContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pdfContent);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new PdfSigningException("Failed to calculate PDF digest", e);
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
        } catch (IOException e) {
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

    /**
     * Parses the ISO 8601 timestamp from CSC API response.
     */
    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }
}
