package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Calendar;

/**
 * Computes the PDF byte range digest required for PAdES deferred signing.
 *
 * <p>Uses Apache PDFBox to prepare the PDF's signature placeholder and extract
 * the byte range that will be covered by the signature.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PadesDigestComputer {

    private static final COSName SUBFILTER = COSName.getPDFName("ETSI.CAdES.detached");
    private static final String SIGNATURE_REASON = "Thai e-Tax Invoice Digital Signature";
    private static final String SIGNATURE_LOCATION = "Thailand";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * Computes the byte range digest of a PDF for deferred signing.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the PDF with PDFBox using a temp file</li>
     *   <li>Create a signature dictionary with placeholder</li>
     *   <li>Call {@code saveIncrementalForExternalSigning} to get the byte range</li>
     *   <li>Compute SHA-256 digest of the byte range</li>
     * </ol>
     *
     * @param pdf InputStream containing the unsigned PDF
     * @return SHA-256 digest of the PDF byte range (32 bytes)
     * @throws IOException if PDF cannot be processed
     */
    public byte[] computeByteRangeDigest(InputStream pdf) throws IOException {
        Path tempFile = null;
        PDDocument document = null;
        IOException primaryException = null;

        try {
            byte[] pdfBytes = pdf.readAllBytes();

            tempFile = Files.createTempFile("pdf-sign-", ".pdf");
            tempFile.toFile().deleteOnExit();
            Files.write(tempFile, pdfBytes);

            document = Loader.loadPDF(tempFile.toFile());

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(SUBFILTER);
            signature.setReason(SIGNATURE_REASON);
            signature.setLocation(SIGNATURE_LOCATION);
            signature.setSignDate(Calendar.getInstance());

            document.addSignature(signature);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ExternalSigningSupport externalSigning =
                document.saveIncrementalForExternalSigning(output);

            try (InputStream dataToSign = externalSigning.getContent()) {
                byte[] data = dataToSign.readAllBytes();
                return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(data);
            } catch (Exception e) {
                throw new IOException("Failed to compute PDF digest", e);
            }

        } catch (IOException e) {
            primaryException = e;
            throw e;
        } finally {
            Exception cleanupException = null;

            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    cleanupException = e;
                    log.warn("Failed to close PDF document: {}", e.getMessage());
                }
            }

            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {} (will be cleaned up on JVM exit)", tempFile);
                    if (cleanupException == null) {
                        cleanupException = e;
                    }
                }
            }

            if (primaryException != null && cleanupException != null) {
                primaryException.addSuppressed(cleanupException);
            }
        }
    }
}
