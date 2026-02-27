package com.wpanther.pdfsigning.domain.model;

/**
 * Exception thrown when a signing operation fails.
 * <p>
 * Used throughout the domain layer to indicate signing-related errors
 * regardless of the underlying infrastructure (CSC API, certificate issues, etc.)
 * </p>
 */
public class SigningException extends RuntimeException {

    public SigningException(String message) {
        super(message);
    }

    public SigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
