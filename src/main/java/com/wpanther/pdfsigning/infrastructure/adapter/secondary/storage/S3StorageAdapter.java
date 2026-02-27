package com.wpanther.pdfsigning.infrastructure.adapter.secondary.storage;

import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import com.wpanther.pdfsigning.domain.model.StorageException;
import com.wpanther.pdfsigning.domain.port.DocumentStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
public class S3StorageAdapter implements DocumentStoragePort {

    private final S3Client s3Client;
    private final String bucketName;
    private final String baseUrl;

    public S3StorageAdapter(
        @Value("${app.storage.s3.bucket-name}") String bucketName,
        @Value("${app.storage.s3.region}") String region,
        @Value("${app.storage.s3.access-key}") String accessKey,
        @Value("${app.storage.s3.secret-key}") String secretKey,
        @Value("${app.storage.s3.endpoint:}") String endpoint,
        @Value("${app.storage.s3.path-style-access:false}") boolean pathStyleAccess,
        @Value("${app.storage.s3.base-url:}") String baseUrl
    ) {
        this.bucketName = bucketName;
        this.baseUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "https://s3." + region + ".amazonaws.com/" + bucketName + "/";

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        var s3Builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (endpoint != null && !endpoint.isEmpty()) {
            s3Builder.endpointOverride(URI.create(endpoint));
            log.info("Using custom S3 endpoint: {}", endpoint);
        }

        if (pathStyleAccess) {
            s3Builder.forcePathStyle(true);
            log.info("Using path-style access for S3");
        }

        this.s3Client = s3Builder.build();

        log.info("Initialized S3 document storage adapter: bucket={}, region={}", bucketName, region);
    }

    @Override
    public String store(byte[] documentData, String documentType, SignedPdfDocument document) {
        try {
            String documentId = document != null ? document.getId().getValue().toString() : "unknown";
            String key = generateKey(documentType, documentId);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/pdf")
                .contentLength((long) documentData.length)
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(documentData));

            // Generate simple URL (not presigned)
            String url = baseUrl + key;
            log.info("Stored document in S3: type={}, bucket={}, key={}, size={} bytes",
                documentType, bucketName, key, documentData.length);

            return url;

        } catch (S3Exception e) {
            log.error("Failed to store document in S3", e);
            throw new StorageException("Failed to store document in S3: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] retrieve(String storageUrl) {
        try {
            // Extract key from pre-signed URL or use directly if it's just a key
            String key = extractKeyFromUrl(storageUrl);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
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
        try {
            String key = extractKeyFromUrl(storageUrl);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Deleted document from S3: bucket={}, key={}", bucketName, key);

        } catch (Exception e) {
            log.error("Failed to delete document from S3", e);
            throw new StorageException("Failed to delete document: " + e.getMessage(), e);
        }
    }

    /**
     * Generate S3 key: {documentType}/YYYY/MM/DD/{documentType}-{documentId}.pdf
     */
    private String generateKey(String documentType, String documentId) {
        LocalDate now = LocalDate.now();
        String sanitizedType = documentType.toLowerCase().replace("_", "-");
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
