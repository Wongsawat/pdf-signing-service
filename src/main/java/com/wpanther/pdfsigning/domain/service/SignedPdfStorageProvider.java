package com.wpanther.pdfsigning.domain.service;

/**
 * Storage abstraction for signed PDF documents.
 * Implementations provide local filesystem or S3 storage.
 */
public interface SignedPdfStorageProvider {

    /**
     * Stores a signed PDF and returns the storage path and public URL.
     *
     * @param signedPdf  the signed PDF bytes
     * @param documentId unique identifier for the document
     * @return storage result with path and URL
     * @throws StorageException if storage fails
     */
    StorageResult store(byte[] signedPdf, String documentId);

    /**
     * Deletes a previously stored signed PDF.
     *
     * @param path the storage path returned by {@link #store}
     * @throws StorageException if deletion fails
     */
    void delete(String path);

    record StorageResult(String path, String url) {}

    class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
