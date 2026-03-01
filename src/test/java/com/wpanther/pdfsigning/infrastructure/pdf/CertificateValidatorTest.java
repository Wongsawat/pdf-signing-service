package com.wpanther.pdfsigning.infrastructure.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        validator.setValidationEnabled(false);
    }

    @Nested
    @DisplayName("validateChain() method")
    class ValidateChainMethod {

        @Test
        @DisplayName("Should reject null certificate chain")
        void shouldRejectNullChain() {
            validator.setValidationEnabled(true);

            assertThatThrownBy(() -> validator.validateChain(null))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should reject empty certificate chain")
        void shouldRejectEmptyChain() {
            validator.setValidationEnabled(true);

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
        @DisplayName("Should validate valid certificate chain successfully")
        void shouldValidateValidCertificateChain() throws Exception {
            validator.setValidationEnabled(true);

            // Given - a valid certificate chain with explicit dates
            // Using explicit dates far in the future to avoid timing issues
            Date pastDate = new Date(System.currentTimeMillis() - 86400000); // Yesterday
            Date futureDate = new Date(System.currentTimeMillis() + 86400000L * 365); // 1 year from now

            X509Certificate endEntity = mock(X509Certificate.class);
            when(endEntity.getNotBefore()).thenReturn(pastDate);
            when(endEntity.getNotAfter()).thenReturn(futureDate);
            when(endEntity.getKeyUsage()).thenReturn(new boolean[]{true, true});
            when(endEntity.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=EndEntity"));
            when(endEntity.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Intermediate"));

            X509Certificate intermediate = mock(X509Certificate.class);
            when(intermediate.getNotBefore()).thenReturn(pastDate);
            when(intermediate.getNotAfter()).thenReturn(futureDate);
            when(intermediate.getKeyUsage()).thenReturn(new boolean[]{true, true});
            when(intermediate.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Intermediate"));
            when(intermediate.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Intermediate"));

            X509Certificate[] certChain = new X509Certificate[]{endEntity, intermediate};

            // When/Then - should not throw
            validator.validateChain(certChain);
        }

        @Test
        @DisplayName("Should validate certificate with one certificate")
        void shouldValidateSingleCertificate() throws Exception {
            validator.setValidationEnabled(true);

            // Given - single certificate chain (self-signed)
            // Using explicit dates far in the future to avoid timing issues
            Date pastDate = new Date(System.currentTimeMillis() - 86400000); // Yesterday
            Date futureDate = new Date(System.currentTimeMillis() + 86400000L * 30); // 30 days from now

            X509Certificate cert = mock(X509Certificate.class);
            when(cert.getNotBefore()).thenReturn(pastDate);
            when(cert.getNotAfter()).thenReturn(futureDate);
            when(cert.getKeyUsage()).thenReturn(new boolean[]{true, true});
            when(cert.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=SelfSigned"));
            when(cert.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=SelfSigned"));

            X509Certificate[] certChain = new X509Certificate[]{cert};

            // When/Then - should not throw
            validator.validateChain(certChain);
        }
    }

    @Nested
    @DisplayName("validateEndEntityCertificate validation logic")
    class ValidateEndEntityCertificateLogic {

        @Test
        @DisplayName("Should reject certificate not yet valid")
        void shouldRejectNotYetValidCertificate() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate that starts in the future
            X509Certificate futureCert = createMockCertificate(
                new Date(System.currentTimeMillis() + 86400000), // notBefore = tomorrow
                new Date(System.currentTimeMillis() + 172800000)  // notAfter = day after tomorrow
            );
            X509Certificate[] certChain = new X509Certificate[]{futureCert};

            // When/Then
            assertThatThrownBy(() -> validator.validateChain(certChain))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("not yet valid");
        }

        @Test
        @DisplayName("Should reject expired certificate")
        void shouldRejectExpiredCertificate() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate that expired yesterday
            X509Certificate expiredCert = createMockCertificate(
                new Date(System.currentTimeMillis() - 172800000), // notBefore = 2 days ago
                new Date(System.currentTimeMillis() - 86400000)   // notAfter = yesterday
            );
            X509Certificate[] certChain = new X509Certificate[]{expiredCert};

            // When/Then
            assertThatThrownBy(() -> validator.validateChain(certChain))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should warn when certificate expires soon (less than 7 days)")
        void shouldWarnWhenCertificateExpiresSoon() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate expiring in 3 days (less than default 7 days)
            long now = System.currentTimeMillis();
            X509Certificate shortLivedCert = createMockCertificate(
                new Date(now - 86400000),             // valid from yesterday
                new Date(now + 86400000 * 3)          // expires in 3 days
            );

            // Set up other required mocks
            try {
                lenient().when(shortLivedCert.getEncoded()).thenReturn(new byte[100]);
                lenient().doNothing().when(shortLivedCert).verify(any(PublicKey.class));
            } catch (Exception e) {
                // Ignore
            }

            X509Certificate[] certChain = new X509Certificate[]{shortLivedCert};

            // When/Then - should not throw, just log warning
            validator.validateChain(certChain);
            // Test passes if no exception is thrown
        }

        @Test
        @DisplayName("Should handle certificate without digitalSignature key usage")
        void shouldHandleCertificateWithoutDigitalSignatureKeyUsage() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate without digitalSignature bit set
            long now = System.currentTimeMillis();
            X509Certificate cert = mock(X509Certificate.class);
            lenient().when(cert.getNotBefore()).thenReturn(new Date(now - 86400000));
            lenient().when(cert.getNotAfter()).thenReturn(new Date(now + 86400000 * 365)); // 1 year from now
            lenient().when(cert.getKeyUsage()).thenReturn(new boolean[]{false, true}); // no digitalSignature

            try {
                lenient().when(cert.getEncoded()).thenReturn(new byte[100]);
                lenient().doNothing().when(cert).verify(any(PublicKey.class));
            } catch (Exception e) {
                // Ignore
            }

            X509Certificate[] certChain = new X509Certificate[]{cert};

            // When/Then - should not throw, just log warning
            validator.validateChain(certChain);
        }

        @Test
        @DisplayName("Should handle certificate without nonRepudiation key usage")
        void shouldHandleCertificateWithoutNonRepudiationKeyUsage() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate without nonRepudiation bit set
            long now = System.currentTimeMillis();
            X509Certificate cert = mock(X509Certificate.class);
            lenient().when(cert.getNotBefore()).thenReturn(new Date(now - 86400000));
            lenient().when(cert.getNotAfter()).thenReturn(new Date(now + 86400000 * 365)); // 1 year from now
            lenient().when(cert.getKeyUsage()).thenReturn(new boolean[]{true, false}); // no nonRepudiation

            try {
                lenient().when(cert.getEncoded()).thenReturn(new byte[100]);
                lenient().doNothing().when(cert).verify(any(PublicKey.class));
            } catch (Exception e) {
                // Ignore
            }

            X509Certificate[] certChain = new X509Certificate[]{cert};

            // When/Then - should not throw, just log warning
            validator.validateChain(certChain);
        }

        @Test
        @DisplayName("Should handle certificate with null extended key usage")
        void shouldHandleNullExtendedKeyUsage() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate with null extended key usage
            long now = System.currentTimeMillis();
            X509Certificate cert = mock(X509Certificate.class);
            lenient().when(cert.getNotBefore()).thenReturn(new Date(now - 86400000));
            lenient().when(cert.getNotAfter()).thenReturn(new Date(now + 86400000 * 365)); // 1 year from now
            lenient().when(cert.getKeyUsage()).thenReturn(new boolean[]{true, true});
            lenient().when(cert.getExtendedKeyUsage()).thenReturn(null);

            try {
                lenient().when(cert.getEncoded()).thenReturn(new byte[100]);
                lenient().doNothing().when(cert).verify(any(PublicKey.class));
            } catch (Exception e) {
                // Ignore
            }

            X509Certificate[] certChain = new X509Certificate[]{cert};

            // When/Then - should not throw
            validator.validateChain(certChain);
        }

        @Test
        @DisplayName("Should handle certificate without PDF signing EKU")
        void shouldHandleCertificateWithoutPdfSigningEku() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate without PDF signing extended key usage
            long now = System.currentTimeMillis();
            X509Certificate cert = mock(X509Certificate.class);
            lenient().when(cert.getNotBefore()).thenReturn(new Date(now - 86400000));
            lenient().when(cert.getNotAfter()).thenReturn(new Date(now + 86400000 * 365)); // 1 year from now
            lenient().when(cert.getKeyUsage()).thenReturn(new boolean[]{true, true});
            lenient().when(cert.getExtendedKeyUsage()).thenReturn(java.util.Collections.singletonList("1.2.840.113549.1.1.1")); // Some other EKU

            try {
                lenient().when(cert.getEncoded()).thenReturn(new byte[100]);
                lenient().doNothing().when(cert).verify(any(PublicKey.class));
            } catch (Exception e) {
                // Ignore
            }

            X509Certificate[] certChain = new X509Certificate[]{cert};

            // When/Then - should not throw
            validator.validateChain(certChain);
        }

        @Test
        @DisplayName("Should handle exception when checking extended key usage")
        void shouldHandleExtendedKeyUsageException() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate that throws exception on getExtendedKeyUsage()
            long now = System.currentTimeMillis();
            X509Certificate cert = mock(X509Certificate.class);
            lenient().when(cert.getNotBefore()).thenReturn(new Date(now - 86400000));
            lenient().when(cert.getNotAfter()).thenReturn(new Date(now + 86400000 * 365)); // 1 year from now
            lenient().when(cert.getKeyUsage()).thenReturn(new boolean[]{true, true});
            lenient().when(cert.getExtendedKeyUsage()).thenThrow(new RuntimeException("EKU check failed"));

            try {
                lenient().when(cert.getEncoded()).thenReturn(new byte[100]);
                lenient().doNothing().when(cert).verify(any(PublicKey.class));
            } catch (Exception e) {
                // Ignore
            }

            X509Certificate[] certChain = new X509Certificate[]{cert};

            // When/Then - should not throw, just log debug message
            validator.validateChain(certChain);
        }

        @Test
        @DisplayName("Should handle generic exception during validation")
        void shouldHandleGenericExceptionDuringValidation() throws Exception {
            validator.setValidationEnabled(true);

            // Given - certificate that throws unexpected exception during validation
            long now = System.currentTimeMillis();
            X509Certificate cert = mock(X509Certificate.class);
            lenient().when(cert.getNotBefore()).thenThrow(new RuntimeException("Unexpected error"));

            X509Certificate[] certChain = new X509Certificate[]{cert};

            // When/Then - should wrap in CertificateValidationException
            assertThatThrownBy(() -> validator.validateChain(certChain))
                .isInstanceOf(CertificateValidator.CertificateValidationException.class)
                .hasMessageContaining("Certificate chain validation failed");
        }
    }

    @Nested
    @DisplayName("validateChainStructure validation logic")
    class ValidateChainStructureLogic {

        @Test
        @DisplayName("Should verify certificate chain structure")
        void shouldVerifyChainStructure() throws Exception {
            validator.setValidationEnabled(true);

            // Given - a proper certificate chain with explicit dates
            // Using explicit dates far in the future to avoid timing issues
            Date pastDate = new Date(System.currentTimeMillis() - 86400000); // Yesterday
            Date futureDate = new Date(System.currentTimeMillis() + 86400000L * 365); // 1 year from now

            X509Certificate endEntity = mock(X509Certificate.class);
            when(endEntity.getNotBefore()).thenReturn(pastDate);
            when(endEntity.getNotAfter()).thenReturn(futureDate);
            when(endEntity.getKeyUsage()).thenReturn(new boolean[]{true, true});
            when(endEntity.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=EndEntity"));
            when(endEntity.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Issuer CA"));

            X509Certificate issuerCert = mock(X509Certificate.class);
            when(issuerCert.getNotBefore()).thenReturn(pastDate);
            when(issuerCert.getNotAfter()).thenReturn(futureDate);
            when(issuerCert.getKeyUsage()).thenReturn(new boolean[]{true, true});
            when(issuerCert.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Issuer CA"));
            when(issuerCert.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Issuer CA"));

            X509Certificate[] certChain = new X509Certificate[]{endEntity, issuerCert};

            // When/Then - should not throw
            validator.validateChain(certChain);
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
        @DisplayName("Should use default max validity days when CscProperties is null")
        void shouldUseDefaultMaxValidityDays() throws Exception {
            // Given - validator with null CscProperties (test mode)
            CertificateValidator newValidator = new CertificateValidator();
            newValidator.setValidationEnabled(true);

            // Given - certificate with validity period longer than default (365 days)
            long now = System.currentTimeMillis();
            X509Certificate longLivedCert = createMockCertificate(
                new Date(now - 86400000),  // valid from yesterday
                new Date(now + 86400000 * 400)  // expires in 400 days (exceeds 365)
            );

            // Add required mocks
            lenient().when(longLivedCert.getEncoded()).thenReturn(new byte[100]);
            lenient().doNothing().when(longLivedCert).verify(any(PublicKey.class));

            X509Certificate[] certChain = new X509Certificate[]{longLivedCert};

            // When/Then - should not throw, just log warning
            newValidator.validateChain(certChain);
            // Test passes if no exception is thrown (warning is logged for exceeding 365 days)
        }

        @Test
        @DisplayName("Should use default min validity remaining days when CscProperties is null")
        void shouldUseDefaultMinValidityRemainingDays() throws Exception {
            // Given - validator with null CscProperties (test mode)
            CertificateValidator newValidator = new CertificateValidator();
            newValidator.setValidationEnabled(true);

            // Given - certificate expiring in less than default 7 days (e.g., 3 days)
            long now = System.currentTimeMillis();
            X509Certificate shortLivedCert = createMockCertificate(
                new Date(now - 86400000),  // valid from yesterday
                new Date(now + 86400000 * 3)  // expires in 3 days (less than 7)
            );

            // Add required mocks
            lenient().when(shortLivedCert.getEncoded()).thenReturn(new byte[100]);
            lenient().doNothing().when(shortLivedCert).verify(any(PublicKey.class));

            X509Certificate[] certChain = new X509Certificate[]{shortLivedCert};

            // When/Then - should not throw, just log warning
            newValidator.validateChain(certChain);
            // Test passes if no exception is thrown (warning is logged for < 7 days remaining)
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

    // Helper methods to create mock certificates

    /**
     * Creates a mock X509Certificate with the specified validity period.
     * Note: getIssuerDN() and getSubjectDN() are NOT stubbed here - callers can stub as needed.
     */
    private X509Certificate createMockCertificate(Date notBefore, Date notAfter) {
        X509Certificate mockCert = mock(X509Certificate.class);

        // Use lenient() to allow stubbing without strict verification
        lenient().when(mockCert.getNotBefore()).thenReturn(notBefore);
        lenient().when(mockCert.getNotAfter()).thenReturn(notAfter);
        lenient().when(mockCert.getKeyUsage()).thenReturn(new boolean[]{true, true}); // digitalSignature, nonRepudiation

        try {
            // Mock encoded form
            lenient().when(mockCert.getEncoded()).thenReturn(new byte[100]);
            // Make verify() succeed for self-signed certificate
            lenient().doNothing().when(mockCert).verify(any(PublicKey.class));
            lenient().when(mockCert.getExtendedKeyUsage()).thenReturn(java.util.Collections.singletonList("1.2.840.113583.1.1.8"));
        } catch (Exception e) {
            // Ignore - this is just mock setup
        }

        return mockCert;
    }

    /**
     * Creates a valid certificate chain with proper issuer relationships.
     * All dates are calculated relative to current time.
     */
    private X509Certificate[] createValidCertificateChain() throws Exception {
        long now = System.currentTimeMillis();

        // Create mock certificates where each cert verifies the next
        X509Certificate endEntity = createMockCertificate(
            new Date(now - 86400000),      // valid from yesterday
            new Date(now + 86400000 * 30)   // valid for 30 days from now
        );

        X509Certificate intermediate = createMockCertificate(
            new Date(now - 86400000 * 365),  // valid from 1 year ago
            new Date(now + 86400000 * 365 * 2) // valid for 2 more years
        );

        // Set up issuer and subject relationships (use lenient to avoid stubbing conflicts)
        lenient().when(endEntity.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=EndEntity"));
        lenient().when(endEntity.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Intermediate"));
        lenient().when(intermediate.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Intermediate"));
        lenient().when(intermediate.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Intermediate")); // Self-signed

        X509Certificate[] chain = new X509Certificate[]{endEntity, intermediate};
        return chain;
    }

    /**
     * Creates a proper certificate chain with verification working.
     * All dates are calculated relative to current time.
     */
    private X509Certificate[] createProperCertificateChain() throws Exception {
        long now = System.currentTimeMillis();

        X509Certificate endEntity = createMockCertificate(
            new Date(now - 86400000),        // valid from yesterday
            new Date(now + 86400000 * 30)    // valid for 30 days from now
        );

        X509Certificate issuerCert = createMockCertificate(
            new Date(now - 86400000 * 365),  // valid from 1 year ago
            new Date(now + 86400000 * 365 * 2) // valid for 2 more years
        );

        // Set up proper issuer/subject relationship
        lenient().when(endEntity.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=EndEntity"));
        lenient().when(endEntity.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Issuer CA"));
        lenient().when(issuerCert.getSubjectDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Issuer CA"));
        lenient().when(issuerCert.getIssuerDN()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Issuer CA")); // Self-signed

        // Set up verification to work
        PublicKey issuerKey = mock(PublicKey.class);
        lenient().when(issuerCert.getPublicKey()).thenReturn(issuerKey);

        try {
            lenient().doNothing().when(endEntity).verify(issuerKey);
            lenient().doNothing().when(issuerCert).verify(any(PublicKey.class)); // Self-signed root
        } catch (Exception e) {
            // Ignore
        }

        return new X509Certificate[]{endEntity, issuerCert};
    }
}
