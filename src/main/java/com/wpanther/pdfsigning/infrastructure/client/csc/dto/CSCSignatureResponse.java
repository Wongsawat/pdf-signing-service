package com.wpanther.pdfsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API v2.0 signHash response DTO.
 *
 * Contains the raw signature value(s) and certificate chain from CSC.
 * The raw signature is used to build the CMS/PKCS#7 structure locally.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CSCSignatureResponse {

    /**
     * Signature algorithm used (e.g., "1.2.840.113549.1.1.11" for SHA256withRSA).
     */
    @JsonProperty("signatureAlgorithm")
    private String signatureAlgorithm;

    /**
     * Array of base64-encoded raw signature values.
     * For single hash request, this array contains exactly one signature.
     *
     * This is the raw RSA/ECDSA signature value, NOT a CMS structure.
     * The CMS structure must be built locally using BouncyCastle.
     */
    @JsonProperty("signatures")
    private String[] signatures;

    /**
     * PEM-encoded certificate chain.
     * May contain multiple certificates in PEM format.
     * Format: "-----BEGIN CERTIFICATE-----...-----END CERTIFICATE-----"
     *
     * Parse this string to extract X509Certificate objects for CMS construction.
     */
    @JsonProperty("certificate")
    private String certificate;

    /**
     * Optional: Operation ID for asynchronous operations.
     */
    @JsonProperty("operationID")
    private String operationID;

    /**
     * Optional: Timestamp data from TSA (if requested).
     * Not used in PAdES-B-B implementation.
     */
    @JsonProperty("timestampData")
    private Object timestampData;
}
