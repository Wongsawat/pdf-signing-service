package com.wpanther.pdfsigning.infrastructure.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for PDF signing behavior.
 * <p>
 * Groups signing-related configuration including retry limits
 * and signing constraints.
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.signing")
public class SigningProperties {

    /**
     * Maximum number of retry attempts for failed signing operations.
     * After exceeding this limit, the document will be marked as permanently failed.
     * Default: 3
     */
    @Min(value = 0, message = "Max retries must be non-negative")
    @Max(value = 10, message = "Max retries must not exceed 10")
    private int maxRetries = 3;
}
