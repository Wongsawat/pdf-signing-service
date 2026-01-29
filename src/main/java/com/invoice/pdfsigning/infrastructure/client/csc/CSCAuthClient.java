package com.invoice.pdfsigning.infrastructure.client.csc;

import com.invoice.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.invoice.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API authorization endpoints.
 *
 * Handles OAuth2 authorization and SAD (Signature Activation Data) token acquisition.
 */
@FeignClient(
        name = "csc-auth-client",
        url = "${app.csc.service-url}",
        configuration = com.invoice.pdfsigning.infrastructure.config.FeignConfig.class
)
public interface CSCAuthClient {

    /**
     * Authorizes a signing operation and obtains a SAD token.
     *
     * @param request the authorization request
     * @return the authorization response containing the SAD token
     */
    @PostMapping("${app.csc.auth-endpoint}")
    CSCAuthorizeResponse authorize(@RequestBody CSCAuthorizeRequest request);
}
