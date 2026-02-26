package com.wpanther.pdfsigning.infrastructure.client;

import com.wpanther.pdfsigning.domain.model.PadesLevel;
import com.wpanther.pdfsigning.domain.service.PdfSigningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PadesLevel configuration parsing.
 */
@DisplayName("PadesLevel Configuration Tests")
class PdfSigningServiceImplTest {

    @Nested
    @DisplayName("PadesLevel parsing")
    class PadesLevelParsing {

        @Test
        @DisplayName("Should parse BASELINE_B from config")
        void shouldParseBaselineB() {
            PadesLevel level = PadesLevel.fromCode("PAdES-BASELINE-B");
            assertThat(level.getCode()).isEqualTo("PAdES-BASELINE-B");
            assertThat(level).isEqualTo(PadesLevel.BASELINE_B);
        }

        @Test
        @DisplayName("Should parse BASELINE_T from config")
        void shouldParseBaselineT() {
            PadesLevel level = PadesLevel.fromCode("PAdES-BASELINE-T");
            assertThat(level).isEqualTo(PadesLevel.BASELINE_T);
            assertThat(level.requiresTimestamp()).isTrue();
        }

        @Test
        @DisplayName("Should parse BASELINE_LT from config")
        void shouldParseBaselineLT() {
            PadesLevel level = PadesLevel.fromCode("PAdES-BASELINE-LT");
            assertThat(level).isEqualTo(PadesLevel.BASELINE_LT);
            assertThat(level.requiresValidationData()).isTrue();
        }

        @Test
        @DisplayName("Should parse BASELINE_LTA from config")
        void shouldParseBaselineLTA() {
            PadesLevel level = PadesLevel.fromCode("PAdES-BASELINE-LTA");
            assertThat(level).isEqualTo(PadesLevel.BASELINE_LTA);
            assertThat(level.requiresArchiveTimestamp()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception for unknown PAdES level")
        void shouldThrowForUnknownLevel() {
            assertThatThrownBy(() -> PadesLevel.fromCode("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown PAdES level");
        }
    }

    @Nested
    @DisplayName("PadesLevel requirements")
    class PadesLevelRequirements {

        @Test
        @DisplayName("BASELINE_B should not require timestamp")
        void baselineBShouldNotRequireTimestamp() {
            assertThat(PadesLevel.BASELINE_B.requiresTimestamp()).isFalse();
        }

        @Test
        @DisplayName("BASELINE_T should require timestamp")
        void baselineTShouldRequireTimestamp() {
            assertThat(PadesLevel.BASELINE_T.requiresTimestamp()).isTrue();
        }

        @Test
        @DisplayName("BASELINE_LT should require validation data")
        void baselineLTShouldRequireValidationData() {
            assertThat(PadesLevel.BASELINE_LT.requiresValidationData()).isTrue();
        }

        @Test
        @DisplayName("BASELINE_LTA should require archive timestamp")
        void baselineLTAShouldRequireArchiveTimestamp() {
            assertThat(PadesLevel.BASELINE_LTA.requiresArchiveTimestamp()).isTrue();
        }
    }
}
