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
        return PdfTempFiles.withTempFile("pdf-sign-", tmp -> {
            Files.write(tmp, pdfBytes);
            try (PDDocument document = Loader.loadPDF(tmp.toFile())) {
                PDSignature signature = buildSignatureDict();
                document.addSignature(signature);

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ExternalSigningSupport externalSigning =
                    document.saveIncrementalForExternalSigning(output);

                externalSigning.setSignature(cmsSignature);
                return output.toByteArray();
            }
        });
    }

    private PDSignature buildSignatureDict() {
        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(SUBFILTER);
        signature.setReason(SIGNATURE_REASON);
        signature.setLocation(SIGNATURE_LOCATION);
        signature.setSignDate(Calendar.getInstance());
        return signature;
    }
}
