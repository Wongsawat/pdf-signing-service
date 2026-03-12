package com.wpanther.pdfsigning.infrastructure.adapter.out.storage;

import com.wpanther.pdfsigning.domain.model.DocumentType;
import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import com.wpanther.pdfsigning.domain.model.StorageException;
import com.wpanther.pdfsigning.application.port.out.DocumentStoragePort;
import com.wpanther.pdfsigning.infrastructure.config.properties.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Secondary adapter for local filesystem document storage.
 * <p>
 * Implements {@link DocumentStoragePort} using the local filesystem.
 * Stores files in {basePath}/YYYY/MM/DD/signed-pdf-{documentId}.pdf
 * </p>
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LocalStorageAdapter implements DocumentStoragePort {

    private final StorageProperties storageProperties;

    @Override
    public String store(byte[] documentData, DocumentType documentType, SignedPdfDocument document) {
        try {
            String basePath = storageProperties.getLocal().getBasePath();
            String baseUrl = storageProperties.getLocal().getBaseUrl();

            LocalDateTime now = LocalDateTime.now();
            String year = String.format("%04d", now.getYear());
            String month = String.format("%02d", now.getMonthValue());
            String day = String.format("%02d", now.getDayOfMonth());

            Path directory = Paths.get(basePath, year, month, day);
            Files.createDirectories(directory);

            String documentId = document != null ? document.getId().getValue().toString() : "unknown";
            String filename = String.format("%s-%s.pdf", documentType.getValue().toLowerCase(), documentId);
            Path filePath = directory.resolve(filename);

            Files.write(filePath, documentData);

            String path = filePath.toString();
            String relativePath = path.substring(basePath.length());
            String url = baseUrl + "/documents" + relativePath.replace("\\", "/");

            log.info("Stored document locally: type={}, path={}, size={} bytes", documentType, path, documentData.length);
            return url;

        } catch (Exception e) {
            log.error("Failed to store document to local filesystem", e);
            throw new StorageException("Failed to store document to local filesystem: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] retrieve(String storageUrl) {
        try {
            String basePath = storageProperties.getLocal().getBasePath();
            String baseUrl = storageProperties.getLocal().getBaseUrl();

            // Convert URL back to filesystem path
            String relativeUrl = storageUrl.substring(baseUrl.length() + "/documents".length());
            String path = basePath + relativeUrl.replace("/", Path.of("/").toString());
            Path filePath = Path.of(path);

            if (!Files.exists(filePath)) {
                throw new StorageException("Document not found: " + storageUrl);
            }

            byte[] content = Files.readAllBytes(filePath);
            log.debug("Retrieved document from local storage: {} bytes", content.length);
            return content;

        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve document from local filesystem", e);
            throw new StorageException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageUrl) {
        try {
            String basePath = storageProperties.getLocal().getBasePath();
            String baseUrl = storageProperties.getLocal().getBaseUrl();

            // Convert URL back to filesystem path
            if (!storageUrl.startsWith(baseUrl)) {
                // Assume storageUrl is already a filesystem path
                Path filePath = Path.of(storageUrl);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("Deleted document from local storage: {}", storageUrl);
                }
                return;
            }

            String relativeUrl = storageUrl.substring(baseUrl.length() + "/documents".length());
            String path = basePath + relativeUrl.replace("/", Path.of("/").toString());
            Path filePath = Path.of(path);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted document from local storage: {}", path);
            }

        } catch (Exception e) {
            log.error("Failed to delete document from local filesystem", e);
            throw new StorageException("Failed to delete document: " + e.getMessage(), e);
        }
    }
}
