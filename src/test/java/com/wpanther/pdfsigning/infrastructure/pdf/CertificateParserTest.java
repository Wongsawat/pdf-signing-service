package com.wpanther.pdfsigning.infrastructure.pdf;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CertificateParser.
 *
 * Tests parsing of PEM-encoded certificate chains from CSC responses.
 */
@DisplayName("CertificateParser Tests")
class CertificateParserTest {

    private CertificateParser certificateParser;

    @BeforeEach
    void setUp() {
        certificateParser = new CertificateParser();
    }

    @Nested
    @DisplayName("parseCertificateChain() method")
    class ParseCertificateChainMethod {

        @Test
        @DisplayName("Should parse valid PEM certificate chain")
        void shouldParseValidPemCertificateChain() throws Exception {
            // Given - generate a real certificate and convert to PEM
            String validPem = generateCertificatePem();

            // When
            X509Certificate[] certChain = certificateParser.parseCertificateChain(validPem);

            // Then
            assertThat(certChain).isNotEmpty();
            assertThat(certChain).hasSize(1);
            assertThat(certChain[0]).isNotNull();
        }

        @Test
        @DisplayName("Should parse PEM certificate chain with multiple certificates")
        void shouldParseMultipleCertificates() throws Exception {
            // Given - two certificates in PEM format
            String cert1 = generateCertificatePem();
            String cert2 = generateCertificatePem();
            String multiCertPem = cert1 + cert2;

            // When
            X509Certificate[] certChain = certificateParser.parseCertificateChain(multiCertPem);

            // Then
            assertThat(certChain).isNotEmpty();
            assertThat(certChain).hasSize(2);
        }

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
        @DisplayName("Should throw exception for null input")
        void shouldThrowExceptionForNullInput() {
            // When/Then - implementation throws NPE for null
            assertThatThrownBy(() -> certificateParser.parseCertificateChain(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should throw exception for PEM without certificate content")
        void shouldThrowExceptionForPemWithoutCertificate() {
            // Given - valid PEM wrapper but no actual certificate
            String pemWithoutCert = "-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----";

            // When/Then
            assertThatThrownBy(() -> certificateParser.parseCertificateChain(pemWithoutCert))
                .isInstanceOf(IOException.class);
                // The error message varies, just check exception type
        }
    }

    @Nested
    @DisplayName("getSigningCertificate() method")
    class GetSigningCertificateMethod {

        @Test
        @DisplayName("Should return null for empty chain")
        void shouldReturnNullForEmptyChain() {
            // Given
            X509Certificate[] emptyChain = new X509Certificate[0];

            // When
            X509Certificate signingCert = certificateParser.getSigningCertificate(emptyChain);

            // Then
            assertThat(signingCert).isNull();
        }

        @Test
        @DisplayName("Should throw exception for null chain")
        void shouldThrowExceptionForNullChain() {
            // When/Then - implementation throws NPE for null
            assertThatThrownBy(() -> certificateParser.getSigningCertificate(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getIssuerCertificate() method")
    class GetIssuerCertificateMethod {

        @Test
        @DisplayName("Should return null for empty chain")
        void shouldReturnNullForEmptyChain() {
            // Given
            X509Certificate[] emptyChain = new X509Certificate[0];

            // When
            X509Certificate issuerCert = certificateParser.getIssuerCertificate(emptyChain);

            // Then
            assertThat(issuerCert).isNull();
        }

        @Test
        @DisplayName("Should throw exception for null chain")
        void shouldThrowExceptionForNullChain() {
            // When/Then - implementation throws NPE for null
            assertThatThrownBy(() -> certificateParser.getIssuerCertificate(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should return null for single certificate chain")
        void shouldReturnNullForSingleCertChain() {
            // Given - a chain with only one certificate (no issuer)
            X509Certificate[] singleCertChain = new X509Certificate[]{null}; // Mock placeholder

            // When
            X509Certificate issuerCert = certificateParser.getIssuerCertificate(singleCertChain);

            // Then
            assertThat(issuerCert).isNull();
        }
    }

    // Helper methods

    /**
     * Generates a test certificate in PEM format.
     */
    private String generateCertificatePem() throws Exception {
        // Register BouncyCastle provider if not already registered
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }

        // Generate key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        // Create self-signed certificate
        org.bouncycastle.x509.X509V3CertificateGenerator certGen =
            new org.bouncycastle.x509.X509V3CertificateGenerator();

        certGen.setSerialNumber(java.math.BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setIssuerDN(new javax.security.auth.x500.X500Principal("CN=Test"));
        certGen.setSubjectDN(new javax.security.auth.x500.X500Principal("CN=Test"));
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 86400000L));
        certGen.setNotAfter(new Date(System.currentTimeMillis() + 86400000L));
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");

        X509Certificate cert = certGen.generate(keyPair.getPrivate());

        // Convert to PEM format
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(cert);
        }

        return stringWriter.toString();
    }
}

