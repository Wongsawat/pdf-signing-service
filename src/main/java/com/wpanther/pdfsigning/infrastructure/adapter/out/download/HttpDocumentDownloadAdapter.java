package com.wpanther.pdfsigning.infrastructure.adapter.out.download;

import com.wpanther.pdfsigning.domain.model.SigningException;
import com.wpanther.pdfsigning.application.port.out.DocumentDownloadPort;
import com.wpanther.pdfsigning.infrastructure.config.properties.PadesProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP-based adapter for downloading PDF documents.
 * <p>
 * This adapter implements the DocumentDownloadPort using standard Java
 * {@link HttpURLConnection}, avoiding external HTTP client dependencies.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class HttpDocumentDownloadAdapter implements DocumentDownloadPort {

    private static final Logger log = LoggerFactory.getLogger(HttpDocumentDownloadAdapter.class);
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final PadesProperties padesProperties;

    /**
     * Package-private constructor for test access.
     * Uses default PadesProperties (maxSizeBytes = 5 MB).
     */
    HttpDocumentDownloadAdapter() {
        this.padesProperties = new PadesProperties();
    }

    /**
     * PDF file header bytes: %PDF-
     * PDF files must start with these 5 bytes (0x25 = %, 0x50 = P, 0x44 = D, 0x46 = F, 0x2D = -)
     */
    private static final byte[] PDF_HEADER = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D};

    @Override
    public byte[] downloadPdf(String url) {
        log.debug("Downloading PDF from URL: {}", redact(url));

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
                    throw new SigningException("Failed to download PDF: HTTP " + responseCode + " from " + redact(url));
                }

                int contentLength = connection.getContentLength();
                long maxSizeBytes = padesProperties.getMaxSizeBytes();
                if (contentLength > maxSizeBytes) {
                    throw new SigningException("PDF too large: " + contentLength + " bytes (max: " + maxSizeBytes + ")");
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    byte[] pdfBytes = readBounded(inputStream, maxSizeBytes);

                    if (pdfBytes.length == 0) {
                        throw new SigningException("Received empty PDF from URL: " + redact(url));
                    }

                    // PDF validation - check for %PDF- header (5 bytes minimum)
                    validatePdfHeader(pdfBytes, redact(url));

                    log.debug("Downloaded PDF: {} bytes from {}", pdfBytes.length, redact(url));
                    return pdfBytes;
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new SigningException("Failed to download PDF from URL: " + redact(url), e);
        }
    }

    /**
     * Reads all bytes from an input stream, enforcing a maximum size limit.
     * <p>
     * When the Content-Length header is absent (chunked encoding, contentLength == -1),
     * the size is unknown upfront. This method protects against unbounded memory
     * allocation by throwing if the download exceeds maxSizeBytes.</p>
     *
     * @param inputStream  The input stream to read from
     * @param maxSizeBytes Maximum allowed bytes (from PadesProperties)
     * @return All downloaded bytes
     * @throws SigningException if the downloaded content exceeds maxSizeBytes
     */
    private byte[] readBounded(InputStream inputStream, long maxSizeBytes) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(Math.toIntExact(Math.min(maxSizeBytes, Integer.MAX_VALUE)));
        byte[] chunk = new byte[8192];
        int bytesRead;
        long totalRead = 0;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            totalRead += bytesRead;
            if (totalRead > maxSizeBytes) {
                throw new SigningException("PDF exceeds maximum size: " + totalRead + " bytes (max: " + maxSizeBytes + ")");
            }
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Returns the URL with any query parameters stripped.
     * Presigned URLs contain HMAC credentials in query params — never include
     * those in logs or exception messages.
     */
    private String redact(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
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
