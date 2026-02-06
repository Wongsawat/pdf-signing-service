package com.wpanther.pdfsigning.domain.model;

/**
 * Represents the status of PDF signing process.
 *
 * State Machine:
 * PENDING → SIGNING → COMPLETED
 *                  → FAILED
 */
public enum SigningStatus {
    /**
     * Initial state - PDF signing request received but not yet started
     */
    PENDING,

    /**
     * PDF signing in progress via CSC API
     */
    SIGNING,

    /**
     * PDF successfully signed and stored
     */
    COMPLETED,

    /**
     * PDF signing failed (max retries exhausted or unrecoverable error)
     */
    FAILED
}
