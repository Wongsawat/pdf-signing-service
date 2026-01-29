package com.invoice.pdfsigning.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Value Object representing a unique identifier for a SignedPdfDocument.
 *
 * This is a type-safe wrapper around UUID that provides domain semantics.
 */
@Getter
@EqualsAndHashCode
@ToString
public class SignedPdfDocumentId {
    private final UUID value;

    private SignedPdfDocumentId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("SignedPdfDocumentId cannot be null");
        }
        this.value = value;
    }

    /**
     * Creates a new SignedPdfDocumentId with a random UUID.
     *
     * @return new SignedPdfDocumentId
     */
    public static SignedPdfDocumentId generate() {
        return new SignedPdfDocumentId(UUID.randomUUID());
    }

    /**
     * Creates a SignedPdfDocumentId from an existing UUID.
     *
     * @param value the UUID value
     * @return SignedPdfDocumentId
     * @throws IllegalArgumentException if value is null
     */
    public static SignedPdfDocumentId of(UUID value) {
        return new SignedPdfDocumentId(value);
    }

    /**
     * Creates a SignedPdfDocumentId from a string representation.
     *
     * @param value the string UUID
     * @return SignedPdfDocumentId
     * @throws IllegalArgumentException if value is null or not a valid UUID
     */
    public static SignedPdfDocumentId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("SignedPdfDocumentId string cannot be null or empty");
        }
        try {
            return new SignedPdfDocumentId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + value, e);
        }
    }

    /**
     * Returns the string representation of this ID.
     *
     * @return UUID as string
     */
    public String asString() {
        return value.toString();
    }
}
