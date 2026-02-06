package com.wpanther.pdfsigning.infrastructure.client.csc;

import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignDocumentRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignDocumentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API signature endpoints.
 *
 * Handles PDF document signing operations using PAdES format.
 */
@FeignClient(
        name = "csc-api-client",
        url = "${app.csc.service-url}",
        configuration = com.wpanther.pdfsigning.infrastructure.config.FeignConfig.class
)
public interface CSCApiClient {

    /**
     * Signs a PDF document using PAdES format.
     *
     * @param request the sign document request
     * @return the sign document response containing the signed PDF
     */
    @PostMapping("${app.csc.sign-endpoint}")
    CSCSignDocumentResponse signDocument(@RequestBody CSCSignDocumentRequest request);
}
