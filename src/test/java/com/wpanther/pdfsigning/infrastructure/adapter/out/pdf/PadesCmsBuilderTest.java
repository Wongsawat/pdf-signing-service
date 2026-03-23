package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PadesCmsBuilder} and its supporting classes
 * {@link PrecomputedContentSigner} and {@link PadesSignedAttributesGenerator}.
 */
@DisplayName("PadesCmsBuilder Tests")
class PadesCmsBuilderTest {

    private PadesCmsBuilder cmsBuilder;

    @BeforeEach
    void setUp() {
        cmsBuilder = new PadesCmsBuilder();
    }

    @Nested
    @DisplayName("buildCmsSignature() method")
    class BuildCmsSignatureMethod {

        @Test
        @DisplayName("Should throw exception for null raw signature")
        void shouldThrowExceptionForNullRawSignature() {
            // Given
            byte[] digest = new byte[32];

            // When/Then
            assertThatThrownBy(() -> cmsBuilder.buildCmsSignature(null, createMockCertificateChain(), digest))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should throw exception for empty certificate chain")
        void shouldThrowExceptionForEmptyCertChain() {
            // Given
            byte[] rawSignature = new byte[256];
            byte[] digest = new byte[32];

            // When/Then
            assertThatThrownBy(() -> cmsBuilder.buildCmsSignature(rawSignature, new X509Certificate[0], digest))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should throw exception for null digest")
        void shouldThrowExceptionForNullDigest() {
            // Given
            byte[] rawSignature = new byte[256];

            // When/Then
            assertThatThrownBy(() -> cmsBuilder.buildCmsSignature(rawSignature, createMockCertificateChain(), null))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should throw exception for mock certificates (invalid encoding)")
        void shouldThrowForMockCertificates() {
            // Given - mock certificates don't work with JcaCertStore
            byte[] rawSignature = new byte[256];
            X509Certificate[] certChain = createMockCertificateChain();
            byte[] digest = new byte[32];

            // When/Then
            assertThatThrownBy(() -> cmsBuilder.buildCmsSignature(rawSignature, certChain, digest))
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("PrecomputedContentSigner")
    class PrecomputedContentSignerTests {

        @Test
        @DisplayName("Should derive SHA256withRSA algorithm identifier from RSA certificate")
        void shouldCreateSignerWithRsaAlgorithmIdentifier() throws Exception {
            // Given
            byte[] signature = new byte[256];
            KeyPair keyPair = generateRsaKeyPair();
            X509Certificate rsaCert = generateRsaSelfSignedCertificate(keyPair);

            // When
            PrecomputedContentSigner signer = createPrecomputedContentSigner(signature, rsaCert);

            // Then - RSA cert → SHA256withRSA OID (1.2.840.113549.1.1.11)
            assertThat(signer.getAlgorithmIdentifier()).isNotNull();
            assertThat(signer.getAlgorithmIdentifier().getAlgorithm().getId())
                .isEqualTo("1.2.840.113549.1.1.11");
        }

        @Test
        @DisplayName("Should derive SHA256withECDSA algorithm identifier from EC certificate")
        void shouldCreateSignerWithEcdsaAlgorithmIdentifier() throws Exception {
            // Given
            byte[] signature = new byte[72];
            KeyPair ecKeyPair = generateEcKeyPair();
            X509Certificate ecCert = generateEcSelfSignedCertificate(ecKeyPair);

            // When
            PrecomputedContentSigner signer = createPrecomputedContentSigner(signature, ecCert);

            // Then - EC cert → SHA256withECDSA OID (1.2.840.10045.4.3.2)
            assertThat(signer.getAlgorithmIdentifier()).isNotNull();
            assertThat(signer.getAlgorithmIdentifier().getAlgorithm().getId())
                .isEqualTo("1.2.840.10045.4.3.2");
        }

        @Test
        @DisplayName("Should throw SigningException for unsupported key algorithm")
        void shouldThrowForUnsupportedKeyAlgorithm() {
            // Given
            byte[] signature = new byte[256];
            X509Certificate mockCert = mock(X509Certificate.class);
            java.security.PublicKey mockKey = mock(java.security.PublicKey.class);
            when(mockCert.getPublicKey()).thenReturn(mockKey);
            when(mockKey.getAlgorithm()).thenReturn("DSA"); // unsupported

            // When/Then
            assertThatThrownBy(() -> createPrecomputedContentSigner(signature, mockCert))
                .isInstanceOf(com.wpanther.pdfsigning.domain.model.SigningException.class)
                .hasMessageContaining("Unsupported signing key algorithm: DSA");
        }

        @Test
        @DisplayName("Should return pre-computed signature bytes")
        void shouldReturnPrecomputedSignature() {
            // Given
            byte[] signature = new byte[]{1, 2, 3, 4, 5};

            // When
            PrecomputedContentSigner signer = createPrecomputedContentSigner(signature, mockRsaCertificate());

            // Then
            assertThat(signer.getSignature()).isEqualTo(signature);
        }

        @Test
        @DisplayName("Should return ByteArrayOutputStream for output")
        void shouldReturnByteArrayOutputStream() {
            // When
            PrecomputedContentSigner signer = createPrecomputedContentSigner(new byte[256], mockRsaCertificate());

            // Then
            assertThat(signer.getOutputStream()).isInstanceOf(java.io.ByteArrayOutputStream.class);
        }

        @Test
        @DisplayName("Should support writing to output stream")
        void shouldSupportWritingToOutputStream() throws Exception {
            // Given
            PrecomputedContentSigner signer = createPrecomputedContentSigner(new byte[256], mockRsaCertificate());

            // When
            signer.getOutputStream().write(new byte[]{1, 2, 3});

            // Then - should not throw
            assertThat(signer.getOutputStream().toString()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("PadesSignedAttributesGenerator")
    class PadesSignedAttributesGeneratorTests {

        @Test
        @DisplayName("Should create generator successfully")
        void shouldCreateGenerator() {
            // When
            PadesSignedAttributesGenerator generator =
                createPadesSignedAttributesGenerator(mock(X509Certificate.class), new byte[32]);

            // Then
            assertThat(generator).isNotNull();
        }

        @Test
        @DisplayName("Should return all required PAdES signed attributes")
        void shouldReturnRequiredAttributes() throws Exception {
            // Given
            KeyPair keyPair = generateRsaKeyPair();
            X509Certificate cert = generateRsaSelfSignedCertificate(keyPair);
            PadesSignedAttributesGenerator generator =
                createPadesSignedAttributesGenerator(cert, new byte[32]);

            // When
            AttributeTable attributes = generator.getAttributes(Collections.emptyMap());

            // Then
            assertThat(attributes).isNotNull();
            Hashtable<?, ?> attrsHashtable = attributes.toHashtable();
            assertThat(attrsHashtable.containsKey(PKCSObjectIdentifiers.pkcs_9_at_contentType)).isTrue();
            assertThat(attrsHashtable.containsKey(PKCSObjectIdentifiers.pkcs_9_at_messageDigest)).isTrue();
            assertThat(attrsHashtable.containsKey(PKCSObjectIdentifiers.pkcs_9_at_signingTime)).isTrue();
            assertThat(attrsHashtable.containsKey(PKCSObjectIdentifiers.id_aa_signingCertificateV2)).isTrue();
        }

        @Test
        @DisplayName("Should throw exception when certificate encoding fails")
        void shouldThrowForCertificateEncodingFailure() throws Exception {
            // Given
            X509Certificate mockCert = mock(X509Certificate.class);
            when(mockCert.getEncoded()).thenThrow(new java.security.cert.CertificateEncodingException("Test error"));
            PadesSignedAttributesGenerator generator =
                createPadesSignedAttributesGenerator(mockCert, new byte[32]);

            // When/Then
            assertThatThrownBy(() -> generator.getAttributes(Collections.emptyMap()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to build PAdES signed attributes");
        }
    }

    // --- Helper methods ---

    private X509Certificate[] createMockCertificateChain() {
        X509Certificate mockCert = mock(X509Certificate.class);
        return new X509Certificate[]{mockCert};
    }

    private X509Certificate mockRsaCertificate() {
        X509Certificate mockCert = mock(X509Certificate.class);
        java.security.PublicKey mockKey = mock(java.security.PublicKey.class);
        when(mockCert.getPublicKey()).thenReturn(mockKey);
        when(mockKey.getAlgorithm()).thenReturn("RSA");
        return mockCert;
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private KeyPair generateEcKeyPair() throws Exception {
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        return keyGen.generateKeyPair();
    }

    private X509Certificate generateRsaSelfSignedCertificate(KeyPair keyPair) throws Exception {
        org.bouncycastle.x509.X509V3CertificateGenerator certGen =
            new org.bouncycastle.x509.X509V3CertificateGenerator();
        certGen.setSerialNumber(java.math.BigInteger.valueOf(1));
        certGen.setIssuerDN(new javax.security.auth.x500.X500Principal("CN=Test"));
        certGen.setSubjectDN(new javax.security.auth.x500.X500Principal("CN=Test"));
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 86400000L));
        certGen.setNotAfter(new Date(System.currentTimeMillis() + 86400000L));
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
        registerBouncyCastle();
        return certGen.generate(keyPair.getPrivate());
    }

    private X509Certificate generateEcSelfSignedCertificate(KeyPair keyPair) throws Exception {
        org.bouncycastle.x509.X509V3CertificateGenerator certGen =
            new org.bouncycastle.x509.X509V3CertificateGenerator();
        certGen.setSerialNumber(java.math.BigInteger.valueOf(2));
        certGen.setIssuerDN(new javax.security.auth.x500.X500Principal("CN=TestEC"));
        certGen.setSubjectDN(new javax.security.auth.x500.X500Principal("CN=TestEC"));
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 86400000L));
        certGen.setNotAfter(new Date(System.currentTimeMillis() + 86400000L));
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithECDSA");
        registerBouncyCastle();
        return certGen.generate(keyPair.getPrivate());
    }

    private void registerBouncyCastle() {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    private PrecomputedContentSigner createPrecomputedContentSigner(byte[] signature, X509Certificate cert) {
        try {
            java.lang.reflect.Constructor<PrecomputedContentSigner> constructor =
                PrecomputedContentSigner.class.getDeclaredConstructor(byte[].class, X509Certificate.class);
            constructor.setAccessible(true);
            return constructor.newInstance(signature, cert);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new RuntimeException("Failed to create PrecomputedContentSigner", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PrecomputedContentSigner", e);
        }
    }

    private PadesSignedAttributesGenerator createPadesSignedAttributesGenerator(
            X509Certificate certificate, byte[] digest) {
        try {
            java.lang.reflect.Constructor<PadesSignedAttributesGenerator> constructor =
                PadesSignedAttributesGenerator.class.getDeclaredConstructor(X509Certificate.class, byte[].class);
            constructor.setAccessible(true);
            return constructor.newInstance(certificate, digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PadesSignedAttributesGenerator", e);
        }
    }
}
