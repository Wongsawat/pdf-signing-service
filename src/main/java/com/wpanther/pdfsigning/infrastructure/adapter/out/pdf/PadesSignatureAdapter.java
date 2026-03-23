package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import com.wpanther.pdfsigning.domain.model.SigningException;
import com.wpanther.pdfsigning.application.port.out.PdfGenerationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * Secondary adapter for PDF-specific processing.
 * <p>
 * Implements {@link PdfGenerationPort} by composing:
 * <ul>
 *   <li>{@link PadesDigestComputer} — computes the PDF byte range digest</li>
 *   <li>{@link PadesEmbedder} — embeds the CMS signature into the PDF</li>
 * </ul>
 * </p>
 * <p>
 * This adapter isolates all PDFBox dependencies from the domain layer.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PadesSignatureAdapter implements PdfGenerationPort {

    private final PadesDigestComputer digestComputer;
    private final PadesEmbedder pdfEmbedder;

    @Override
    public byte[] computeByteRangeDigest(byte[] pdfBytes) {
        log.debug("Computing PDF byte range digest for {} bytes", pdfBytes.length);
        try {
            byte[] digest = digestComputer.computeByteRangeDigest(
                new ByteArrayInputStream(pdfBytes)
            );
            log.debug("Computed digest: {} bytes", digest.length);
            return digest;
        } catch (SigningException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error computing byte range digest", e);
            throw new SigningException("Failed to compute PDF digest: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] embedSignature(byte[] pdfBytes, byte[] cmsSignature) {
        log.debug("Embedding CMS signature into PDF ({} bytes)", cmsSignature.length);
        try {
            byte[] signedPdf = pdfEmbedder.embedSignature(pdfBytes, cmsSignature);
            log.debug("Embedded signature, signed PDF size: {} bytes", signedPdf.length);
            return signedPdf;
        } catch (SigningException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error embedding signature into PDF", e);
            throw new SigningException("Failed to embed signature: " + e.getMessage(), e);
        }
    }
}
