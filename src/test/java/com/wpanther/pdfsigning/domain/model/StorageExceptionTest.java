package com.wpanther.pdfsigning.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StorageException}.
 */
@DisplayName("StorageException Tests")
class StorageExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void shouldCreateWithMessage() {
        // When
        StorageException exception = new StorageException("Storage failed");

        // Then
        assertThat(exception.getMessage()).isEqualTo("Storage failed");
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateWithMessageAndCause() {
        // Given
        Throwable cause = new RuntimeException("Root cause");

        // When
        StorageException exception = new StorageException("Storage failed", cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo("Storage failed");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should be throwable and catchable as StorageException")
    void shouldBeThrowable() {
        // When/Then
        Exception caught = null;
        try {
            throw new StorageException("Test exception");
        } catch (StorageException e) {
            caught = e;
        }

        assertThat(caught).isNotNull();
        assertThat(caught.getMessage()).isEqualTo("Test exception");
    }
}
