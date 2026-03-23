package com.wpanther.pdfsigning.infrastructure.config.properties;

import com.wpanther.pdfsigning.domain.model.PadesLevel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for PAdES (PDF Advanced Electronic Signatures) behavior.
 * <p>
 * Configures the signature conformance level for PDF signing operations.
 * </p>
 * <p>
 * Supported levels (ETSI EN 319 142-1):
 * <ul>
 *   <li>BASELINE_B - Basic signature (minimum for RD compliance)</li>
 *   <li>BASELINE_T - With trusted timestamp</li>
 *   <li>BASELINE_LT - Long-term validation (TSA + OCSP/CRL)</li>
 *   <li>BASELINE_LTA - Archive timestamp (recommended for 5-year retention)</li>
 * </ul>
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.pades")
public class PadesProperties {

    /**
     * PAdES conformance level for PDF signatures.
     * Default: BASELINE_B (minimum level for Thai Revenue Department compliance)
     * <p>
     * Note: Higher levels (T, LT, LTA) require additional services:
     * - BASELINE_T requires a Time Stamping Authority (TSA)
     * - BASELINE_LT requires TSA + OCSP/CRL access
     * - BASELINE_LTA requires all LT services + archive timestamp
     * </p>
     */
    private PadesLevel level = PadesLevel.BASELINE_B;

    /**
     * Maximum allowed size for downloaded PDFs in bytes.
     * Protects the service from memory exhaustion when downloading large files.
     * Default: 5 MB (5,242,880 bytes)
     */
    private long maxSizeBytes = 5 * 1024 * 1024;
}
