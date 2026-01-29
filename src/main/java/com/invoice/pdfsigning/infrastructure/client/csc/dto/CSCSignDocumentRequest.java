package com.invoice.pdfsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API signDocument request DTO.
 *
 * Used to sign a PDF document using PAdES format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CSCSignDocumentRequest {

    /**
     * Credential identifier
     */
    @JsonProperty("credentialID")
    private String credentialID;

    /**
     * Signature Activation Data token from authorize call
     */
    @JsonProperty("SAD")
    private String SAD;

    /**
     * Base64-encoded document to sign
     */
    @JsonProperty("document")
    private String document;

    /**
     * Document digest (Base64-encoded hash)
     */
    @JsonProperty("documentDigest")
    private String documentDigest;

    /**
     * Signature attributes (PAdES-specific)
     */
    @JsonProperty("signatureAttributes")
    private PadesSignatureAttributes signatureAttributes;

    /**
     * Return the signed document in the response
     */
    @JsonProperty("returnSignedDocument")
    @Builder.Default
    private Boolean returnSignedDocument = true;
}
