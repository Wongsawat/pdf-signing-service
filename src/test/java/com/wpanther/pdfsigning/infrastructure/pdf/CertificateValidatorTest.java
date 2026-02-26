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
    }
}
