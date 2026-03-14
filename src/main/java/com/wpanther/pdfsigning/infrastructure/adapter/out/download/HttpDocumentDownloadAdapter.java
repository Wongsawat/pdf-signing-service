package com.wpanther.pdfsigning.infrastructure.adapter.out.download;

import com.wpanther.pdfsigning.domain.model.SigningException;
import com.wpanther.pdfsigning.application.port.out.DocumentDownloadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP-based adapter for downloading PDF documents.
 * <p>
 * This adapter implements the DocumentDownloadPort using standard Java
 * {@link HttpURLConnection}, avoiding external HTTP client dependencies.
 * </p>
 */
@Component
public class HttpDocumentDownloadAdapter implements DocumentDownloadPort {

    private static final Logger log = LoggerFactory.getLogger(HttpDocumentDownloadAdapter.class);
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_PDF_SIZE = 100 * 1024 * 1024; // 100 MB

    /**
     * PDF file header bytes: %PDF-
     * PDF files must start with these 5 bytes (0x25 = %, 0x50 = P, 0x44 = D, 0x46 = F, 0x2D = -)
     */
    private static final byte[] PDF_HEADER = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D};

    @Override
    public byte[] downloadPdf(String url) {
        log.debug("Downloading PDF from URL: {}", url);

        try {
            URL downloadUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();

            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/pdf");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new SigningException("Failed to download PDF: HTTP " + responseCode + " from " + url);
                }

                int contentLength = connection.getContentLength();
                if (contentLength > 0 && contentLength > MAX_PDF_SIZE) {
                    throw new SigningException("PDF too large: " + contentLength + " bytes (max: " + MAX_PDF_SIZE + ")");
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    byte[] pdfBytes = inputStream.readAllBytes();

                    if (pdfBytes.length == 0) {
                        throw new SigningException("Received empty PDF from URL: " + url);
                    }

                    // PDF validation - check for %PDF- header (5 bytes minimum)
                    validatePdfHeader(pdfBytes, url);

                    log.debug("Downloaded PDF: {} bytes from {}", pdfBytes.length, url);
                    return pdfBytes;
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new SigningException("Failed to download PDF from URL: " + url, e);
        }
    }

    /**
     * Validates that the file has a valid PDF header (%PDF-).
     *
     * @param pdfBytes The downloaded file bytes
     * @param url The source URL (for error messages)
     * @throws SigningException if the file is not a valid PDF
     */
    private void validatePdfHeader(byte[] pdfBytes, String url) throws SigningException {
        if (pdfBytes.length < PDF_HEADER.length) {
            throw new SigningException(
                "File too small to be a valid PDF: " + pdfBytes.length + " bytes (min: " + PDF_HEADER.length + ") from " + url);
        }

        for (int i = 0; i < PDF_HEADER.length; i++) {
            if (pdfBytes[i] != PDF_HEADER[i]) {
                throw new SigningException(
                    "Downloaded file is not a valid PDF: invalid header at position " + i + " from " + url);
            }
        }
    }
}
