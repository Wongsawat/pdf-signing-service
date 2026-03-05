package com.wpanther.pdfsigning.domain.port.out;

import com.wpanther.pdfsigning.domain.model.SigningException;

/**
 * Port for downloading PDF documents from storage.
 * <p>
 * This port abstracts the mechanism for retrieving PDF documents,
 * allowing implementations to download from HTTP URLs, S3, local filesystem, etc.
 * </p>
 * <p>
 * In hexagonal architecture, this is a secondary port (driven) that
 * enables the domain to request documents without knowing how they are retrieved.
 * </p>
 */
public interface DocumentDownloadPort {

    /**
     * Downloads a PDF document from the given URL.
     *
     * @param url the URL to download from
     * @return the PDF document bytes
     * @throws SigningException if download fails
     */
    byte[] downloadPdf(String url);
}
