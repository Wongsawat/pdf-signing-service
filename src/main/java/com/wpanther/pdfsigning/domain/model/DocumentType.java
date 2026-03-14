package com.wpanther.pdfsigning.domain.model;

/**
 * Document type enumeration for storage operations.
 * <p>
 * Represents the different types of documents that can be stored
 * in the document storage system.
 * </p>
 */
public enum DocumentType {

    /**
     * Unsigned PDF document (e.g., before signing)
     */
    UNSIGNED_PDF("UNSIGNED_PDF"),

    /**
     * Signed PDF document (e.g., after PAdES signing)
     */
    SIGNED_PDF("SIGNED_PDF"),

    /**
     * Tax Invoice document type
     */
    TAX_INVOICE("TAX_INVOICE"),

    /**
     * Invoice document type
     */
    INVOICE("INVOICE");

    private final String value;

    DocumentType(String value) {
        this.value = value;
    }

    /**
     * Returns the string representation of this document type.
     *
     * @return String value for storage operations
     */
    public String getValue() {
        return value;
    }

    /**
     * Find DocumentType by string value.
     *
     * @param value String value to look up
     * @return Corresponding DocumentType enum
     * @throws IllegalArgumentException if no matching type found
     */
    public static DocumentType fromValue(String value) {
        for (DocumentType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown document type: " + value);
    }
}
