package com.wpanther.pdfsigning.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SigningException}.
 */
@DisplayName("SigningException Tests")
class SigningExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void shouldCreateWithMessage() {
        // When
        SigningException exception = new SigningException("Signing failed");

        // Then
        assertThat(exception.getMessage()).isEqualTo("Signing failed");
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateWithMessageAndCause() {
        // Given
        Throwable cause = new RuntimeException("Root cause");

        // When
        SigningException exception = new SigningException("Signing failed", cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo("Signing failed");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should be throwable and catchable as SigningException")
    void shouldBeThrowable() {
        // When/Then
        Exception caught = null;
        try {
            throw new SigningException("Test exception");
        } catch (SigningException e) {
            caught = e;
        }

        assertThat(caught).isNotNull();
        assertThat(caught.getMessage()).isEqualTo("Test exception");
    }
}
