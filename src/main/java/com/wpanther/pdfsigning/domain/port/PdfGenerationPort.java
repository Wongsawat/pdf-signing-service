package com.wpanther.pdfsigning.domain.port;

import com.wpanther.pdfsigning.domain.model.SigningException;

/**
 * Port for PDF-specific processing operations.
 * <p>
 * Abstracts PDF library (PDFBox) details from the domain.
 * Infrastructure provides implementations using PDFBox or similar.
 * </p>
 */
public interface PdfGenerationPort {

    /**
     * Compute byte range digest for PAdES signature.
     * <p>
     * PAdES signatures cover a specific byte range of the PDF.
     * This method computes that digest before signing.
     * </p>
     *
     * @param pdfBytes Original PDF bytes
     * @return SHA-256 digest of the byte range that will be signed
     * @throws SigningException if PDF processing fails
     */
    byte[] computeByteRangeDigest(byte[] pdfBytes) throws SigningException;

    /**
     * Embed CMS signature into PDF document.
     * <p>
     * Takes a pre-computed CMS/PKCS#7 signature and embeds it
     * into the PDF's signature dictionary.
     * </p>
     *
     * @param pdfBytes     Original unsigned PDF bytes
     * @param cmsSignature CMS/PKCS#7 signature bytes
     * @return Signed PDF bytes with embedded signature
     * @throws SigningException if embedding fails
     */
    byte[] embedSignature(byte[] pdfBytes, byte[] cmsSignature) throws SigningException;
}
