package com.wpanther.pdfsigning.infrastructure.config.feign;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign configuration scoped to {@link com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCAuthClient}.
 *
 * <p>The authorize endpoint is idempotent — retrying the same authorization request
 * with the same hash and credentialID will return the same SAD token (stateless token
 * issuance). A 3-retry strategy is safe and improves resilience to transient network
 * errors.</p>
 *
 * <p>This configuration is intentionally separate from {@link FeignConfig} so that
 * {@link com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCApiClient}
 * does NOT inherit a retrying Retryer. The signHash endpoint is NOT idempotent
 * (HSM signatures are non-deterministic), so Feign-level retries must be disabled
 * for that client to prevent duplicate signature generation.</p>
 */
@Configuration
public class AuthFeignConfig {

    /**
     * Retryer for the CSC authorize endpoint.
     * Retries up to 3 times with 1 second intervals on connection failures and
     * recoverable HTTP status codes (5xx, 408, 0).
     */
    @Bean
    public Retryer authRetryer() {
        return new Retryer.Default(1000, 1000, 3);
    }
}
