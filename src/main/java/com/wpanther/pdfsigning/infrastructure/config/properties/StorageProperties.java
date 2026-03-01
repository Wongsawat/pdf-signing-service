package com.wpanther.pdfsigning.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for document storage backends.
 * <p>
 * Groups storage-related configuration for both local filesystem
 * and S3-compatible storage providers.
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * Storage provider to use.
     * Accepted values: local, s3
     * Default: local
     */
    private String provider = "local";

    /**
     * Local filesystem storage configuration.
     */
    private final Local local = new Local();

    /**
     * S3-compatible storage configuration.
     */
    private final S3 s3 = new S3();

    /**
     * Local filesystem storage configuration.
     */
    @Data
    public static class Local {

        /**
         * Base directory path for storing documents.
         * Documents are stored in {basePath}/YYYY/MM/DD/ structure.
         */
        private String basePath;

        /**
         * Base URL for accessing stored documents via HTTP.
         * Stored documents will be accessible at baseUrl/documents/{path}
         */
        private String baseUrl;
    }

    /**
     * S3-compatible storage configuration.
     */
    @Data
    public static class S3 {

        /**
         * S3 bucket name for storing documents.
         */
        private String bucketName;

        /**
         * AWS region for S3 service.
         */
        private String region;

        /**
         * AWS access key ID for authentication.
         */
        private String accessKey;

        /**
         * AWS secret access key for authentication.
         */
        private String secretKey;

        /**
         * Custom S3 endpoint URL.
         * Useful for S3-compatible services like MinIO.
         * If not specified, uses the default AWS S3 endpoint.
         */
        private String endpoint;

        /**
         * Whether to use path-style access instead of virtual-hosted-style.
         * Path-style: https://s3.amazonaws.com/bucket/key
         * Virtual-hosted-style: https://bucket.s3.amazonaws.com/key
         * Default: false (virtual-hosted-style)
         * Set to true for MinIO and other S3-compatible services.
         */
        private boolean pathStyleAccess = false;

        /**
         * Base URL for accessing stored documents via HTTP.
         * If not specified, constructs URL from endpoint and bucket.
         */
        private String baseUrl;
    }
}
