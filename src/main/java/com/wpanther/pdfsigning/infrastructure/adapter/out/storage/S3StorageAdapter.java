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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.LocalDate;

/**
 * Secondary adapter for AWS S3 / MinIO document storage.
 * <p>
 * Implements {@link DocumentStoragePort} using S3-compatible storage.
 * Stores files with key: {documentType}/YYYY/MM/DD/{documentType}-{documentId}.pdf
 * </p>
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@RequiredArgsConstructor
@Slf4j
public class S3StorageAdapter implements DocumentStoragePort {

    private final StorageProperties storageProperties;
    private S3Client s3Client;
    private String baseUrl;

    /**
     * Initializes the S3 client and base URL from configuration.
     * Called by Spring after properties are injected.
     */
    public void init() {
        StorageProperties.S3 s3 = storageProperties.getS3();
        this.baseUrl = s3.getBaseUrl() != null && !s3.getBaseUrl().isEmpty()
            ? s3.getBaseUrl()
            : "https://s3." + s3.getRegion() + ".amazonaws.com/" + s3.getBucketName() + "/";

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            s3.getAccessKey(),
            s3.getSecretKey()
        );

        var s3Builder = S3Client.builder()
            .region(Region.of(s3.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (s3.getEndpoint() != null && !s3.getEndpoint().isEmpty()) {
            s3Builder.endpointOverride(URI.create(s3.getEndpoint()));
            log.info("Using custom S3 endpoint: {}", s3.getEndpoint());
        }

        if (s3.isPathStyleAccess()) {
            s3Builder.forcePathStyle(true);
            log.info("Using path-style access for S3");
        }

        this.s3Client = s3Builder.build();

        log.info("Initialized S3 document storage adapter: bucket={}, region={}",
            s3.getBucketName(), s3.getRegion());
    }

    /**
     * Constructor for testing with injected S3Client.
     * Package-private for test access only.
     */
    S3StorageAdapter(StorageProperties storageProperties, S3Client s3Client, String baseUrl) {
        this.storageProperties = storageProperties;
        this.s3Client = s3Client;
        this.baseUrl = baseUrl;
    }

    @Override
    public String store(byte[] documentData, DocumentType documentType, SignedPdfDocument document) {
        if (s3Client == null) {
            init();
        }

        try {
            StorageProperties.S3 s3 = storageProperties.getS3();
            String documentId = document != null ? document.getId().getValue().toString() : "unknown";
            String key = generateKey(documentType, documentId);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3.getBucketName())
                .key(key)
                .contentType("application/pdf")
                .contentLength((long) documentData.length)
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(documentData));

            // Generate simple URL (not presigned)
            String url = baseUrl + key;
            log.info("Stored document in S3: type={}, bucket={}, key={}, size={} bytes",
                documentType, s3.getBucketName(), key, documentData.length);

            return url;

        } catch (S3Exception e) {
            log.error("Failed to store document in S3", e);
            throw new StorageException("Failed to store document in S3: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] retrieve(String storageUrl) {
        if (s3Client == null) {
            init();
        }

        try {
            StorageProperties.S3 s3 = storageProperties.getS3();
            // Extract key from pre-signed URL or use directly if it's just a key
            String key = extractKeyFromUrl(storageUrl);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3.getBucketName())
                .key(key)
                .build();

            // Read bytes from the response InputStream
            try (var response = s3Client.getObject(getObjectRequest)) {
                return response.readAllBytes();
            }

        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve document from S3", e);
            throw new StorageException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageUrl) {
        if (s3Client == null) {
            init();
        }

        try {
            StorageProperties.S3 s3 = storageProperties.getS3();
            String key = extractKeyFromUrl(storageUrl);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(s3.getBucketName())
                .key(key)
                .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Deleted document from S3: bucket={}, key={}", s3.getBucketName(), key);

        } catch (Exception e) {
            log.error("Failed to delete document from S3", e);
            throw new StorageException("Failed to delete document: " + e.getMessage(), e);
        }
    }

    /**
     * Generate S3 key: {documentType}/YYYY/MM/DD/{documentType}-{documentId}.pdf
     */
    private String generateKey(DocumentType documentType, String documentId) {
        LocalDate now = LocalDate.now();
        String sanitizedType = documentType.getValue().toLowerCase().replace("_", "-");
        return String.format("%s/%04d/%02d/%02d/%s-%s.pdf",
            sanitizedType, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), sanitizedType, documentId);
    }

    /**
     * Extract S3 key from storage URL.
     * If the URL is a pre-signed URL, extract the key from query parameters.
     * Otherwise, assume it's already a key.
     */
    private String extractKeyFromUrl(String storageUrl) {
        try {
            String bucketName = storageProperties.getS3().getBucketName();
            if (storageUrl.contains("?")) {
                // Pre-signed URL - extract key from URL path
                String pathPart = storageUrl.substring(0, storageUrl.indexOf("?"));
                int bucketIndex = pathPart.indexOf("/" + bucketName + "/");
                if (bucketIndex >= 0) {
                    return pathPart.substring(bucketIndex + bucketName.length() + 1);
                }
            }
            // Assume it's already a key or a simple path
            return storageUrl;
        } catch (Exception e) {
            log.warn("Could not extract key from URL, using as-is: {}", storageUrl);
            return storageUrl;
        }
    }
}
