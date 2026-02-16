package com.wpanther.pdfsigning.infrastructure.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CertificateParser.
 *
 * Tests parsing of PEM-encoded certificate chains from CSC responses.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateParser Tests")
class CertificateParserTest {

    @InjectMocks
    private CertificateParser certificateParser;

    @Test
    @DisplayName("Should throw exception for empty PEM data")
    void shouldThrowExceptionForEmptyPemData() {
        // Given
        String emptyPem = "";

        // When/Then
        assertThatThrownBy(() -> certificateParser.parseCertificateChain(emptyPem))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("No certificates found");
    }

    @Test
    @DisplayName("Should throw exception for invalid PEM data")
    void shouldThrowExceptionForInvalidPemData() {
        // Given
        String invalidPem = "Not a valid PEM certificate";

        // When/Then
        assertThatThrownBy(() -> certificateParser.parseCertificateChain(invalidPem))
            .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should return null for signing certificate from empty chain")
    void shouldReturnNullForSigningCertificateFromEmptyChain() {
        // Given
        X509Certificate[] emptyChain = new X509Certificate[0];

        // When
        X509Certificate signingCert = certificateParser.getSigningCertificate(emptyChain);

        // Then
        assertThat(signingCert).isNull();
    }

    @Test
    @DisplayName("Should return null for issuer certificate from empty chain")
    void shouldReturnNullForIssuerCertificateFromEmptyChain() {
        // Given
        X509Certificate[] emptyChain = new X509Certificate[0];

        // When
        X509Certificate issuerCert = certificateParser.getIssuerCertificate(emptyChain);

        // Then
        assertThat(issuerCert).isNull();
    }
}
