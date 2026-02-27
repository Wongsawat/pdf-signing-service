package com.wpanther.pdfsigning.domain.model;

/**
 * Exception thrown when a document storage operation fails.
 * <p>
 * Used throughout the domain layer to indicate storage-related errors
 * regardless of the underlying storage mechanism (local, S3, etc.)
 * </p>
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
