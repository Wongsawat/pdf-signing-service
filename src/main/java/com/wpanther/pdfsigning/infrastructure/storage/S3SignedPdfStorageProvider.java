package com.wpanther.pdfsigning.infrastructure.storage;

import com.wpanther.pdfsigning.domain.service.SignedPdfStorageProvider;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.LocalDate;

/**
 * AWS S3 / MinIO storage provider for signed PDFs.
 * Stores files with key: signed-pdfs/YYYY/MM/DD/signed-pdf-{documentId}.pdf
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@Slf4j
public class S3SignedPdfStorageProvider implements SignedPdfStorageProvider {

    private final S3Client s3Client;
    private final String bucketName;
    private final String baseUrl;

    public S3SignedPdfStorageProvider(
        @Value("${app.storage.s3.bucket-name}") String bucketName,
        @Value("${app.storage.s3.region}") String region,
        @Value("${app.storage.s3.access-key}") String accessKey,
        @Value("${app.storage.s3.secret-key}") String secretKey,
        @Value("${app.storage.s3.base-url}") String baseUrl,
        @Value("${app.storage.s3.endpoint:}") String endpoint,
        @Value("${app.storage.s3.path-style-access:false}") boolean pathStyleAccess
    ) {
        this.bucketName = bucketName;
        this.baseUrl = baseUrl;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        var builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            log.info("Using custom S3 endpoint: {}", endpoint);
        }

        if (pathStyleAccess) {
            builder.forcePathStyle(true);
            log.info("Using path-style access for S3");
        }

        this.s3Client = builder.build();

        log.info("Initialized S3 signed PDF storage: bucket={}, region={}, endpoint={}",
            bucketName, region, endpoint != null && !endpoint.isEmpty() ? endpoint : "AWS");
    }

    @Override
    public StorageResult store(byte[] signedPdf, String documentId) {
        try {
            String key = generateKey(documentId);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/pdf")
                .contentLength((long) signedPdf.length)
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(signedPdf));

            String url = baseUrl + "/" + key;
            log.info("Stored signed PDF in S3: bucket={}, key={}, size={} bytes", bucketName, key, signedPdf.length);

            return new StorageResult(key, url);

        } catch (S3Exception e) {
            log.error("Failed to store signed PDF in S3: documentId={}", documentId, e);
            throw new StorageException("Failed to store signed PDF in S3", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Deleted signed PDF from S3: bucket={}, key={}", bucketName, path);

        } catch (S3Exception e) {
            log.error("Failed to delete signed PDF from S3: key={}", path, e);
            throw new StorageException("Failed to delete signed PDF from S3", e);
        }
    }

    /**
     * Generate S3 key: signed-pdfs/YYYY/MM/DD/signed-pdf-{documentId}.pdf
     */
    private String generateKey(String documentId) {
        LocalDate now = LocalDate.now();
        return String.format("signed-pdfs/%04d/%02d/%02d/signed-pdf-%s.pdf",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(), documentId);
    }
}
