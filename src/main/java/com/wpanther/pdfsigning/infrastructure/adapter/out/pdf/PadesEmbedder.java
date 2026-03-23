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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;

/**
 * Embeds a CMS signature into a PDF document to produce a signed PDF.
 *
 * <p>Uses Apache PDFBox incremental save to embed the pre-built CMS/PKCS#7 signature
 * into the PDF's signature dictionary without rewriting the entire document.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PadesEmbedder {

    private static final COSName SUBFILTER = COSName.getPDFName("ETSI.CAdES.detached");
    private static final String SIGNATURE_REASON = "Thai e-Tax Invoice Digital Signature";
    private static final String SIGNATURE_LOCATION = "Thailand";

    /**
     * Embeds the CMS signature into the PDF document.
     *
     * @param pdfBytes     Original unsigned PDF bytes
     * @param cmsSignature CMS/PKCS#7 signature bytes from {@link PadesCmsBuilder}
     * @return Signed PDF bytes with embedded PAdES signature
     * @throws IOException if PDF processing fails
     */
    public byte[] embedSignature(byte[] pdfBytes, byte[] cmsSignature) throws IOException {
        Path tempFile = null;
        PDDocument document = null;
        IOException primaryException = null;

        try {
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

            externalSigning.setSignature(cmsSignature);

            return output.toByteArray();

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
