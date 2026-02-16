package com.wpanther.pdfsigning.infrastructure.storage;

import com.wpanther.pdfsigning.domain.service.SignedPdfStorageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Local filesystem storage provider for signed PDFs.
 * Stores files in {basePath}/YYYY/MM/DD/signed-pdf-{documentId}.pdf
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalSignedPdfStorageProvider implements SignedPdfStorageProvider {

    private final String basePath;
    private final String baseUrl;

    public LocalSignedPdfStorageProvider(
        @Value("${app.storage.local.base-path}") String basePath,
        @Value("${app.storage.local.base-url}") String baseUrl
    ) {
        this.basePath = basePath;
        this.baseUrl = baseUrl;
        log.info("Initialized local signed PDF storage: basePath={}, baseUrl={}", basePath, baseUrl);
    }

    @Override
    public StorageResult store(byte[] signedPdf, String documentId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String year = String.format("%04d", now.getYear());
            String month = String.format("%02d", now.getMonthValue());
            String day = String.format("%02d", now.getDayOfMonth());

            Path directory = Paths.get(basePath, year, month, day);
            Files.createDirectories(directory);

            String filename = String.format("signed-pdf-%s.pdf", documentId);
            Path filePath = directory.resolve(filename);

            Files.write(filePath, signedPdf);

            String path = filePath.toString();
            String relativePath = path.substring(basePath.length());
            String url = baseUrl + "/signed-documents" + relativePath.replace("\\", "/");

            log.info("Stored signed PDF locally: path={}, size={} bytes", path, signedPdf.length);
            return new StorageResult(path, url);

        } catch (Exception e) {
            throw new StorageException("Failed to store signed PDF to local filesystem", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path filePath = Path.of(path);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted signed PDF file: {}", path);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to delete signed PDF from local filesystem: " + path, e);
        }
    }
}
