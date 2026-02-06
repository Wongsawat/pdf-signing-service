package com.wpanther.pdfsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API signDocument response DTO.
 *
 * Contains the signed PDF document and signing metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CSCSignDocumentResponse {

    /**
     * Base64-encoded signed document
     */
    @JsonProperty("signedDocument")
    private String signedDocument;

    /**
     * Transaction identifier
     */
    @JsonProperty("transactionID")
    private String transactionID;

    /**
     * PEM-encoded signing certificate
     */
    @JsonProperty("certificate")
    private String certificate;

    /**
     * Timestamp from the signing service (ISO 8601 format)
     */
    @JsonProperty("timestamp")
    private String timestamp;
}
