package com.wpanther.pdfsigning.infrastructure.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Embeds PAdES (PDF Advanced Electronic Signatures) into PDF documents.
 *
 * This class handles:
 * 1. Computing PDF byte range digest for deferred signing
 * 2. Building CMS/PKCS#7 signature containers with raw signatures from CSC
 * 3. Embedding completed signatures into PDF documents
 *
 * Uses Apache PDFBox for PDF processing and BouncyCastle for CMS construction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PadesSignatureEmbedder {

    private static final COSName SUBFILTER = COSName.getPDFName("ETSI.CAdES.detached");
    private static final String SIGNATURE_REASON = "Thai e-Tax Invoice Digital Signature";
    private static final String SIGNATURE_LOCATION = "Thailand";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String SHA256_OID = "2.16.840.1.101.3.4.2.1";

    /**
     * Computes the byte range digest of a PDF for deferred signing.
     *
     * This method:
     * 1. Loads the PDF with PDFBox
     * 2. Creates a signature dictionary with placeholder
     * 3. Uses saveIncrementalForExternalSigning to get the byte range
     * 4. Computes SHA-256 digest of the byte range
     *
     * @param pdf InputStream containing the unsigned PDF
     * @return SHA-256 digest of the PDF byte range (32 bytes)
     * @throws IOException if PDF cannot be processed
     */
    public byte[] computeByteRangeDigest(InputStream pdf) throws IOException {
        // Create temp file for PDFBox 3.0
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-sign-", ".pdf");
            byte[] pdfBytes = pdf.readAllBytes();
            Files.write(tempFile, pdfBytes);

            try (PDDocument document = Loader.loadPDF(tempFile.toFile());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                // Create signature dictionary
                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(SUBFILTER);
                signature.setReason(SIGNATURE_REASON);
                signature.setLocation(SIGNATURE_LOCATION);
                signature.setSignDate(Calendar.getInstance());

                // Add signature to document
                document.addSignature(signature);

                // Prepare for external signing (deferred)
                ExternalSigningSupport externalSigning =
                    document.saveIncrementalForExternalSigning(output);

                // Get the byte range content (everything except /Contents placeholder)
                try (InputStream dataToSign = externalSigning.getContent()) {
                    byte[] data = dataToSign.readAllBytes();
                    return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(data);
                } catch (Exception e) {
                    throw new IOException("Failed to compute PDF digest", e);
                }
            }
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Builds a CMS/PKCS#7 signature container with the raw signature from CSC.
     *
     * This method:
     * 1. Creates a CMSSignedDataGenerator
     * 2. Adds the certificate chain
     * 3. Creates signer info with the raw signature
     * 4. Adds PAdES compliant signed attributes
     * 5. Generates the CMS structure
     *
     * @param rawSignature Raw signature bytes from CSC signHash endpoint
     * @param certChain Certificate chain from CSC response
     * @param digest The PDF byte range digest that was signed
     * @return Encoded CMS/PKCS#7 signature bytes
     * @throws Exception if CMS construction fails
     */
    public byte[] buildCmsSignature(byte[] rawSignature,
                                    X509Certificate[] certChain,
                                    byte[] digest) throws Exception {

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();

        // Add certificate chain
        Store certStore = new JcaCertStore(Arrays.asList(certChain));
        generator.addCertificates(certStore);

        // Create signer info with pre-computed signature
        ContentSigner signer = new PrecomputedContentSigner(rawSignature);

        DigestCalculatorProvider digestProvider =
            new JcaDigestCalculatorProviderBuilder().build();

        SignerInfoGeneratorBuilder signerBuilder =
            new SignerInfoGeneratorBuilder(digestProvider);

        // Add PAdES compliant signed attributes
        signerBuilder.setSignedAttributeGenerator(
            new PadesSignedAttributesGenerator(certChain[0], digest)
        );

        generator.addSignerInfoGenerator(
            signerBuilder.build(signer, new X509CertificateHolder(
                certChain[0].getEncoded()))
        );

        // Generate CMS
        CMSSignedData cms = generator.generate(
            new CMSProcessableByteArray(digest), false
        );

        return cms.getEncoded();
    }

    /**
     * Embeds the CMS signature into the PDF document.
     *
     * @param pdfBytes Original unsigned PDF bytes
     * @param cmsSignature CMS/PKCS#7 signature bytes from buildCmsSignature()
     * @return Signed PDF bytes with embedded PAdES signature
     * @throws IOException if PDF processing fails
     */
    public byte[] embedSignature(byte[] pdfBytes, byte[] cmsSignature) throws IOException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-sign-", ".pdf");
            Files.write(tempFile, pdfBytes);

            try (PDDocument document = Loader.loadPDF(tempFile.toFile());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(SUBFILTER);
                signature.setReason(SIGNATURE_REASON);
                signature.setLocation(SIGNATURE_LOCATION);
                signature.setSignDate(Calendar.getInstance());

                document.addSignature(signature);

                ExternalSigningSupport externalSigning =
                    document.saveIncrementalForExternalSigning(output);

                // Set the CMS signature
                externalSigning.setSignature(cmsSignature);

                return output.toByteArray();
            }
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }
}

/**
 * Helper class for pre-computed signatures from CSC.
 *
 * This ContentSigner implementation returns a signature that was
 * pre-computed by the CSC service, rather than computing it locally.
 */
class PrecomputedContentSigner implements ContentSigner {
    private final byte[] signature;
    private final AlgorithmIdentifier algorithmIdentifier;
    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public PrecomputedContentSigner(byte[] signature) {
        this.signature = signature;
        // SHA256withRSA algorithm identifier
        this.algorithmIdentifier = new AlgorithmIdentifier(
            new ASN1ObjectIdentifier("1.2.840.113549.1.1.11") // SHA256withRSA
        );
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return algorithmIdentifier;
    }
}

/**
 * Generates PAdES compliant signed attributes.
 *
 * Required attributes per ETSI EN 319 142-1:
 * - contentType (id-data): 1.2.840.113549.1.9.3
 * - messageDigest: 1.2.840.113549.1.9.4
 * - signingTime: 1.2.840.113549.1.9.5
 * - signingCertificateV2: 1.2.840.113549.1.9.16.2.47
 */
class PadesSignedAttributesGenerator implements CMSAttributeTableGenerator {

    private static final String SHA256_OID = "2.16.840.1.101.3.4.2.1";
    private final X509Certificate certificate;
    private final byte[] digest;

    public PadesSignedAttributesGenerator(X509Certificate certificate, byte[] digest) {
        this.certificate = certificate;
        this.digest = digest;
    }

    @Override
    public AttributeTable getAttributes(Map params) {
        Hashtable<ASN1ObjectIdentifier, ASN1Encodable> attributes = new Hashtable<>();

        try {
            // 1. contentType attribute - indicates the type of content being signed
            attributes.put(
                PKCSObjectIdentifiers.pkcs_9_at_contentType,
                new ASN1ObjectIdentifier("1.2.840.113549.1.7.1") // id-data
            );

            // 2. messageDigest attribute - digest of the signed content
            attributes.put(
                PKCSObjectIdentifiers.pkcs_9_at_messageDigest,
                new DEROctetString(digest)
            );

            // 3. signingTime attribute - timestamp of signature creation
            attributes.put(
                PKCSObjectIdentifiers.pkcs_9_at_signingTime,
                new org.bouncycastle.asn1.ASN1GeneralizedTime(new Date())
            );

            // 4. signingCertificateV2 attribute - identifies the signer's certificate
            attributes.put(
                PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                buildSigningCertificateV2()
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to build PAdES signed attributes", e);
        }

        return new AttributeTable(attributes);
    }

    /**
     * Builds the signingCertificateV2 attribute.
     * This contains the SHA-256 hash of the signer's certificate.
     */
    private org.bouncycastle.asn1.ASN1Sequence buildSigningCertificateV2() throws Exception {
        // Compute SHA-256 hash of the certificate
        byte[] certDigest = computeCertificateDigest(certificate);

        // Create ESSCertIDv2 structure
        org.bouncycastle.asn1.ASN1EncodableVector certIdVector = new org.bouncycastle.asn1.ASN1EncodableVector();

        // Algorithm identifier for SHA-256
        certIdVector.add(new AlgorithmIdentifier(
            new ASN1ObjectIdentifier(SHA256_OID) // SHA-256 OID
        ));

        // Certificate hash
        certIdVector.add(new DEROctetString(certDigest));

        return new org.bouncycastle.asn1.DERSequence(new org.bouncycastle.asn1.DERSequence(certIdVector));
    }

    /**
     * Computes SHA-256 digest of the certificate.
     */
    private byte[] computeCertificateDigest(X509Certificate cert) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(cert.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute certificate digest", e);
        }
    }
}
