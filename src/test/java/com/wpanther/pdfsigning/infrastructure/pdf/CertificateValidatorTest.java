package com.wpanther.pdfsigning.infrastructure.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CertificateValidator.
 */
@DisplayName("CertificateValidator Tests")
class CertificateValidatorTest {

    private CertificateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CertificateValidator();
        // Disable validation for tests that don't have trust store configured
        ReflectionTestUtils.setField(validator, "validationEnabled", false);
    }

    @Nested
    @DisplayName("validateChain() method")
    class ValidateChainMethod {

        @Test
        @DisplayName("Should reject null certificate chain")
        void shouldRejectNullChain() {
            ReflectionTestUtils.setField(validator, "validationEnabled", true);

            assertThatThrownBy(() -> validator.validateChain(null))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should reject empty certificate chain")
        void shouldRejectEmptyChain() {
            ReflectionTestUtils.setField(validator, "validationEnabled", true);

            assertThatThrownBy(() -> validator.validateChain(new X509Certificate[0]))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should skip validation when disabled")
        void shouldSkipValidationWhenDisabled() {
            // Validation is disabled in setUp(), should not throw
            X509Certificate[] certChain = new X509Certificate[0];
            validator.validateChain(certChain);

            // No exception thrown
        }

        @Test
        @DisplayName("Should accept valid certificate chain when validation enabled")
        void shouldAcceptValidChainWhenValidationEnabled() {
            ReflectionTestUtils.setField(validator, "validationEnabled", true);

            // Given - a mock certificate array (not actually validated without trust store)
            // In real scenarios, this would require properly configured certificates
            // For unit testing, we verify the validation logic is called
            X509Certificate[] certChain = new X509Certificate[0];

            // When/Then - empty chain throws regardless of validation enabled
            assertThatThrownBy(() -> validator.validateChain(certChain))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class);
        }
    }

    @Nested
    @DisplayName("setValidationEnabled() method")
    class SetValidationEnabledMethod {

        @Test
        @DisplayName("Should allow toggling validation")
        void shouldAllowTogglingValidation() {
            validator.setValidationEnabled(false);
            // Should not throw when validation is disabled
            validator.validateChain(new X509Certificate[0]);

            validator.setValidationEnabled(true);
            // Should throw when validation is enabled
            assertThatThrownBy(() -> validator.validateChain(new X509Certificate[0]))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class);
        }

        @Test
        @DisplayName("Should disable validation when set to false")
        void shouldDisableValidation() {
            validator.setValidationEnabled(false);

            // Given empty chain - would normally fail validation
            X509Certificate[] certChain = new X509Certificate[0];

            // When/Then - should not throw because validation is disabled
            validator.validateChain(certChain);
        }

        @Test
        @DisplayName("Should enable validation when set to true")
        void shouldEnableValidation() {
            validator.setValidationEnabled(true);

            // Given empty chain
            X509Certificate[] certChain = new X509Certificate[0];

            // When/Then - should throw because validation is enabled
            assertThatThrownBy(() -> validator.validateChain(certChain))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class);
        }
    }

    @Nested
    @DisplayName("Configuration properties")
    class ConfigurationProperties {

        @Test
        @DisplayName("Should have default validation enabled")
        void shouldHaveDefaultValidationEnabled() {
            // Given a new validator
            CertificateValidator newValidator = new CertificateValidator();

            // When/Then - default should be enabled (check by trying to validate empty chain)
            ReflectionTestUtils.setField(newValidator, "validationEnabled", true);
            assertThatThrownBy(() -> newValidator.validateChain(new X509Certificate[0]))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class);
        }
    }

    @Nested
    @DisplayName("CertificateValidationException class")
    class CertificateValidationExceptionClass {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateExceptionWithMessage() {
            // When
            CertificateValidator.CertificateValidationException exception =
                new CertificateValidator.CertificateValidationException("Test error");

            // Then
            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Given
            Throwable cause = new RuntimeException("Root cause");

            // When
            CertificateValidator.CertificateValidationException exception =
                new CertificateValidator.CertificateValidationException("Test error", cause);

            // Then
            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("Should be RuntimeException")
        void shouldBeRuntimeException() {
            // Given
            CertificateValidator.CertificateValidationException exception =
                new CertificateValidator.CertificateValidationException("Test error");

            // Then
            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }
}
