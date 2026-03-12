package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import com.wpanther.pdfsigning.domain.model.SigningException;
import com.wpanther.pdfsigning.application.port.out.PdfGenerationPort;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.PadesSignatureEmbedder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * Secondary adapter for PDF-specific processing.
 * <p>
 * Implements {@link PdfGenerationPort} using Apache PDFBox for:
 * <ul>
 *   <li>Computing PDF byte range digests for PAdES signatures</li>
 *   <li>Embedding CMS signatures into PDF documents</li>
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

    private final PadesSignatureEmbedder signatureEmbedder;

    @Override
    public byte[] computeByteRangeDigest(byte[] pdfBytes) {
        log.debug("Computing PDF byte range digest for {} bytes", pdfBytes.length);
        try {
            byte[] digest = signatureEmbedder.computeByteRangeDigest(
                new ByteArrayInputStream(pdfBytes)
            );
            log.debug("Computed digest: {} bytes", digest.length);
            return digest;
        } catch (Exception e) {
            log.error("Failed to compute byte range digest", e);
            throw new SigningException("Failed to compute PDF digest: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] embedSignature(byte[] pdfBytes, byte[] cmsSignature) {
        log.debug("Embedding CMS signature into PDF ({} bytes)", cmsSignature.length);
        try {
            byte[] signedPdf = signatureEmbedder.embedSignature(pdfBytes, cmsSignature);
            log.debug("Embedded signature, signed PDF size: {} bytes", signedPdf.length);
            return signedPdf;
        } catch (Exception e) {
            log.error("Failed to embed signature into PDF", e);
            throw new SigningException("Failed to embed signature: " + e.getMessage(), e);
        }
    }
}
