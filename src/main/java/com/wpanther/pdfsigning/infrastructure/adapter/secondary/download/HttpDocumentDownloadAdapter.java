package com.wpanther.pdfsigning.infrastructure.adapter.secondary.download;

import com.wpanther.pdfsigning.domain.model.SigningException;
import com.wpanther.pdfsigning.domain.port.DocumentDownloadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

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

                    // Basic PDF validation - check for %PDF- header
                    if (pdfBytes.length < 4 || !new String(pdfBytes, 0, 4).equals("%PDF")) {
                        throw new SigningException("Downloaded file is not a valid PDF: " + url);
                    }

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
}
