package com.invoice.pdfsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API authorize request DTO.
 *
 * Used to obtain a SAD (Signature Activation Data) token for signing operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CSCAuthorizeRequest {

    /**
     * Credential identifier
     */
    @JsonProperty("credentialID")
    private String credentialID;

    /**
     * Number of signatures to authorize
     */
    @JsonProperty("numSignatures")
    @Builder.Default
    private Integer numSignatures = 1;

    /**
     * Hash array for the documents to be signed (Base64-encoded)
     */
    @JsonProperty("hash")
    private String[] hash;

    /**
     * Description of the authorization request
     */
    @JsonProperty("description")
    private String description;
}
