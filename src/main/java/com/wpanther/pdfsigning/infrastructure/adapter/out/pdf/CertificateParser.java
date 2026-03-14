package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses PEM-encoded certificate chains from CSC responses.
 *
 * This component handles parsing of certificate chains returned by the CSC API
 * in PEM format (-----BEGIN CERTIFICATE-----...-----END CERTIFICATE-----).
 */
@Slf4j
@Component
public class CertificateParser {

    /**
     * Parses a PEM-encoded certificate chain string into X509Certificate array.
     *
     * @param pemCertificate PEM-encoded certificate(s) from CSC
     * @return Array of X509Certificate objects
     * @throws IOException if parsing fails
     */
    public X509Certificate[] parseCertificateChain(String pemCertificate) throws IOException {
        List<X509Certificate> certificates = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(pemCertificate.getBytes(StandardCharsets.UTF_8));
             InputStreamReader isr = new InputStreamReader(bais, StandardCharsets.UTF_8);
             PEMParser parser = new PEMParser(isr)) {

            Object object;
            while ((object = parser.readObject()) != null) {
                if (object instanceof X509CertificateHolder) {
                    X509CertificateHolder holder = (X509CertificateHolder) object;
                    try {
                        X509Certificate cert = new JcaX509CertificateConverter()
                            .getCertificate(holder);
                        certificates.add(cert);
                        log.debug("Parsed certificate: {}", cert.getSubjectDN());
                    } catch (CertificateException e) {
                        throw new IOException("Failed to convert certificate", e);
                    }
                }
            }
        }

        if (certificates.isEmpty()) {
            throw new IOException("No certificates found in PEM data");
        }

        log.info("Parsed certificate chain with {} certificates", certificates.size());
        return certificates.toArray(new X509Certificate[0]);
    }

    /**
     * Gets the end-entity (signing) certificate from the chain.
     * This is typically the first certificate in the chain.
     *
     * @param chain Certificate chain
     * @return The signing certificate or null if chain is empty
     */
    public X509Certificate getSigningCertificate(X509Certificate[] chain) {
        return chain.length > 0 ? chain[0] : null;
    }

    /**
     * Gets the issuer (CA) certificate from the chain.
     * This is typically the last certificate in the chain.
     *
     * @param chain Certificate chain
     * @return The issuer certificate or null if chain has only one certificate
     */
    public X509Certificate getIssuerCertificate(X509Certificate[] chain) {
        return chain.length > 1 ? chain[chain.length - 1] : null;
    }
}
