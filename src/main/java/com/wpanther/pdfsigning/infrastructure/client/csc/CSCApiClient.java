package com.wpanther.pdfsigning.infrastructure.client.csc;

import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API v2.0 signHash endpoint.
 *
 * Uses deferred signing pattern:
 * - PDF byte range digest is computed locally (PDFBox)
 * - Only the hash is sent to CSC
 * - CMS/PKCS#7 structure is built locally (BouncyCastle)
 * - Signature is embedded into PDF locally (PDFBox)
 */
@FeignClient(
        name = "csc-api-client",
        url = "${app.csc.service-url}",
        configuration = com.wpanther.pdfsigning.infrastructure.config.FeignConfig.class
)
public interface CSCApiClient {

    /**
     * Signs a hash value using deferred signing.
     *
     * @param request the sign hash request containing digest to sign
     * @return the sign hash response containing raw signature and certificate chain
     */
    @PostMapping("${app.csc.sign-hash-endpoint}")
    CSCSignatureResponse signHash(@RequestBody CSCSignatureRequest request);
}
