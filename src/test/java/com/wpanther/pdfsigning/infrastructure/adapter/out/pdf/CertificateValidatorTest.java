package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CertificateValidator}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CertificateValidator Tests")
class CertificateValidatorTest {

    @Mock
    private X509Certificate mockCert;

    private CertificateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CertificateValidator();
        validator.setValidationEnabled(true);

        // Default: certificate valid for ±1 year
        when(mockCert.getNotBefore()).thenReturn(Date.from(Instant.now().minusSeconds(86400)));
        when(mockCert.getNotAfter()).thenReturn(Date.from(Instant.now().plusSeconds(86400L * 365)));
    }

    @Nested
    @DisplayName("Key usage enforcement")
    class KeyUsageEnforcement {

        @Test
        @DisplayName("Should throw when digitalSignature bit (0) is missing")
        void shouldThrowWhenDigitalSignatureBitMissing() {
            boolean[] keyUsage = new boolean[9];
            keyUsage[1] = true; // nonRepudiation set, digitalSignature not
            when(mockCert.getKeyUsage()).thenReturn(keyUsage);

            assertThatThrownBy(() -> validator.validateChain(new X509Certificate[]{mockCert}))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("digitalSignature");
        }

        @Test
        @DisplayName("Should throw when nonRepudiation bit (1) is missing")
        void shouldThrowWhenNonRepudiationBitMissing() {
            boolean[] keyUsage = new boolean[9];
            keyUsage[0] = true; // digitalSignature set, nonRepudiation not
            when(mockCert.getKeyUsage()).thenReturn(keyUsage);

            assertThatThrownBy(() -> validator.validateChain(new X509Certificate[]{mockCert}))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("nonRepudiation");
        }

        @Test
        @DisplayName("Should pass when both digitalSignature and nonRepudiation bits are set")
        void shouldPassWhenBothRequiredKeyUsageBitsPresent() {
            boolean[] keyUsage = new boolean[9];
            keyUsage[0] = true; // digitalSignature
            keyUsage[1] = true; // nonRepudiation
            when(mockCert.getKeyUsage()).thenReturn(keyUsage);

            assertThatCode(() -> validator.validateChain(new X509Certificate[]{mockCert}))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should pass when KeyUsage extension is absent (no restriction)")
        void shouldPassWhenKeyUsageExtensionAbsent() {
            when(mockCert.getKeyUsage()).thenReturn(null);

            assertThatCode(() -> validator.validateChain(new X509Certificate[]{mockCert}))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Validity period checks")
    class ValidityPeriodChecks {

        @Test
        @DisplayName("Should throw when certificate is not yet valid")
        void shouldThrowWhenCertificateNotYetValid() {
            when(mockCert.getNotBefore()).thenReturn(Date.from(Instant.now().plusSeconds(86400)));
            when(mockCert.getKeyUsage()).thenReturn(null);

            assertThatThrownBy(() -> validator.validateChain(new X509Certificate[]{mockCert}))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("not yet valid");
        }

        @Test
        @DisplayName("Should throw when certificate has expired")
        void shouldThrowWhenCertificateExpired() {
            when(mockCert.getNotAfter()).thenReturn(Date.from(Instant.now().minusSeconds(86400)));
            when(mockCert.getKeyUsage()).thenReturn(null);

            assertThatThrownBy(() -> validator.validateChain(new X509Certificate[]{mockCert}))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("expired");
        }
    }

    @Nested
    @DisplayName("Validation disabled")
    class ValidationDisabled {

        @Test
        @DisplayName("Should skip all checks when validation is disabled")
        void shouldSkipChecksWhenDisabled() {
            validator.setValidationEnabled(false);
            // Even a null chain passes when disabled
            assertThatCode(() -> validator.validateChain(null))
                .doesNotThrowAnyException();
        }
    }
}
