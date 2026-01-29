package com.invoice.pdfsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PAdES signature attributes for CSC API signDocument request.
 *
 * Specifies the signature format, level, and PDF-specific attributes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PadesSignatureAttributes {

    /**
     * Signature type (PAdES)
     */
    @JsonProperty("signatureType")
    private String signatureType;

    /**
     * Signature level (e.g., PAdES-BASELINE-T)
     */
    @JsonProperty("signatureLevel")
    private String signatureLevel;

    /**
     * Signature form (attached)
     */
    @JsonProperty("signatureForm")
    private String signatureForm;

    /**
     * Digest algorithm (e.g., SHA256)
     */
    @JsonProperty("digestAlgorithm")
    private String digestAlgorithm;

    /**
     * Reason for signing
     */
    @JsonProperty("reason")
    private String reason;

    /**
     * Location where signing occurred
     */
    @JsonProperty("location")
    private String location;

    /**
     * Contact information of signer
     */
    @JsonProperty("contactInfo")
    private String contactInfo;
}
