package com.wpanther.pdfsigning.domain.model;

/**
 * PAdES (PDF Advanced Electronic Signatures) conformance levels.
 *
 * Based on ETSI EN 319 142-1: PAdES Baseline profiles.
 *
 * @see <a href="https://www.etsi.org/deliver/etsi_en/319100_319199/31914201/01.01.01_60/en_31914201v010101p.pdf">ETSI EN 319 142-1</a>
 */
public enum PadesLevel {
    /**
     * PAdES-Baseline-B - Basic signature with signer certificate.
     * Minimum level for Revenue Department compliance.
     * No timestamp or validation data embedded.
     */
    BASELINE_B("PAdES-BASELINE-B", "Basic"),

    /**
     * PAdES-Baseline-T - Basic with trusted timestamp.
     * Requires TSA (Time Stamping Authority) service.
     * Provides proof of signing time.
     */
    BASELINE_T("PAdES-BASELINE-T", "Basic with Timestamp"),

    /**
     * PAdES-Baseline-LT - Long Term validation.
     * Includes TSA timestamp + OCSP/CRL validation data.
     * Enables offline signature verification.
     */
    BASELINE_LT("PAdES-BASELINE-LT", "Long Term"),

    /**
     * PAdES-Baseline-LTA - Long Term with Archive timestamp.
     * Recommended for 5-year document retention.
     * Includes all LT data + archive timestamp.
     */
    BASELINE_LTA("PAdES-BASELINE-LTA", "Long Term Archive");

    private final String code;
    private final String description;

    PadesLevel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if this level requires TSA timestamp.
     */
    public boolean requiresTimestamp() {
        return this == BASELINE_T || this == BASELINE_LT || this == BASELINE_LTA;
    }

    /**
     * Checks if this level requires OCSP/CRL validation data.
     */
    public boolean requiresValidationData() {
        return this == BASELINE_LT || this == BASELINE_LTA;
    }

    /**
     * Checks if this level requires archive timestamp.
     */
    public boolean requiresArchiveTimestamp() {
        return this == BASELINE_LTA;
    }

    /**
     * Gets the PadesLevel from its code.
     *
     * @param code The PAdES level code (e.g., "PAdES-BASELINE-B")
     * @return The corresponding PadesLevel
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static PadesLevel fromCode(String code) {
        for (PadesLevel level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown PAdES level: " + code);
    }
}
