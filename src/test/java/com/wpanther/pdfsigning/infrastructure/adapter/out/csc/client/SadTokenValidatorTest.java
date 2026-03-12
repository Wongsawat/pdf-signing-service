package com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client;

import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SadTokenValidator.
 */
@DisplayName("SadTokenValidator Tests")
class SadTokenValidatorTest {

    private SadTokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SadTokenValidator();
    }

    @Nested
    @DisplayName("validate() method")
    class ValidateMethod {

        @Test
        @DisplayName("Should accept valid SAD token with expiresIn")
        void shouldAcceptValidSadToken() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("valid-base64-token-with-sufficient-length");
            response.setExpiresIn(300L); // 5 minutes

            validator.validate(response, "test-credential");

            // No exception thrown = success
        }

        @Test
        @DisplayName("Should accept valid SAD token without expiresIn")
        void shouldAcceptSadTokenWithoutExpiresIn() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("valid-base64-token-with-sufficient-length");

            validator.validate(response, "test-credential");

            // No exception thrown = success
        }

        @Test
        @DisplayName("Should reject null response")
        void shouldRejectNullResponse() {
            assertThatThrownBy(() -> validator.validate(null, "test-credential"))
                .isInstanceOf(SadTokenValidator.SadTokenValidationException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("Should reject null SAD token")
        void shouldRejectNullSadToken() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();

            assertThatThrownBy(() -> validator.validate(response, "test-credential"))
                .isInstanceOf(SadTokenValidator.SadTokenValidationException.class)
                .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should reject empty SAD token")
        void shouldRejectEmptySadToken() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("   ");

            assertThatThrownBy(() -> validator.validate(response, "test-credential"))
                .isInstanceOf(SadTokenValidator.SadTokenValidationException.class)
                .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should reject SAD token that is too short")
        void shouldRejectShortSadToken() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("short");

            assertThatThrownBy(() -> validator.validate(response, "test-credential"))
                .isInstanceOf(SadTokenValidator.SadTokenValidationException.class)
                .hasMessageContaining("too short");
        }

        @Test
        @DisplayName("Should reject expired SAD token")
        void shouldRejectExpiredSadToken() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("valid-base64-token-with-sufficient-length");
            response.setExpiresIn(0L);

            assertThatThrownBy(() -> validator.validate(response, "test-credential"))
                .isInstanceOf(SadTokenValidator.SadTokenValidationException.class)
                .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should reject negative expiresIn")
        void shouldRejectNegativeExpiresIn() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("valid-base64-token-with-sufficient-length");
            response.setExpiresIn(-1L);

            assertThatThrownBy(() -> validator.validate(response, "test-credential"))
                .isInstanceOf(SadTokenValidator.SadTokenValidationException.class)
                .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should reject SAD token with expiry below minimum")
        void shouldRejectSadTokenBelowMinimum() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("valid-base64-token-with-sufficient-length");
            response.setExpiresIn(30L); // Less than default 60 seconds

            assertThatThrownBy(() -> validator.validate(response, "test-credential"))
                .isInstanceOf(SadTokenValidator.SadTokenValidationException.class)
                .hasMessageContaining("expires too soon")
                .hasMessageContaining("30");
        }

        @Test
        @DisplayName("Should accept SAD token with expiry above maximum (logs warning)")
        void shouldAcceptSadTokenAboveMaximum() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("valid-base64-token-with-sufficient-length");
            response.setExpiresIn(5000L); // More than default 3600 seconds (1 hour)

            // Should not throw exception, but logs a warning
            validator.validate(response, "test-credential");

            // No exception = success (warning logged but not verified in unit test)
        }
    }

    @Nested
    @DisplayName("SadTokenValidationException")
    class SadTokenValidationExceptionTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateExceptionWithMessage() {
            SadTokenValidator.SadTokenValidationException exception =
                new SadTokenValidator.SadTokenValidationException("Test error message");

            assertThat(exception.getMessage()).isEqualTo("Test error message");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            Throwable cause = new IOException("Underlying error");
            SadTokenValidator.SadTokenValidationException exception =
                new SadTokenValidator.SadTokenValidationException("Test error message", cause);

            assertThat(exception.getMessage()).isEqualTo("Test error message");
            assertThat(exception.getCause()).isSameAs(cause);
        }
    }

    @Nested
    @DisplayName("isExpired() method")
    class IsExpiredMethod {

        @Test
        @DisplayName("Should return false for valid token")
        void shouldReturnFalseForValidToken() {
            Instant issuedAt = Instant.now();
            Long expiresIn = 300L;

            boolean expired = validator.isExpired(issuedAt, expiresIn);

            assertThat(expired).isFalse();
        }

        @Test
        @DisplayName("Should return true for expired token")
        void shouldReturnTrueForExpiredToken() {
            Instant issuedAt = Instant.now().minusSeconds(400);
            Long expiresIn = 300L;

            boolean expired = validator.isExpired(issuedAt, expiresIn);

            assertThat(expired).isTrue();
        }

        @Test
        @DisplayName("Should return false when expiresIn is null")
        void shouldReturnFalseWhenExpiresInIsNull() {
            boolean expired = validator.isExpired(Instant.now(), null);

            assertThat(expired).isFalse();
        }

        @Test
        @DisplayName("Should return false when issuedAt is null")
        void shouldReturnFalseWhenIssuedAtIsNull() {
            boolean expired = validator.isExpired(null, 300L);

            assertThat(expired).isFalse();
        }
    }

    @Nested
    @DisplayName("Configuration getters")
    class ConfigurationGetters {

        @Test
        @DisplayName("Should return default min expiry seconds")
        void shouldReturnDefaultMinExpirySeconds() {
            assertThat(validator.getMinExpirySeconds()).isEqualTo(60L);
        }

        @Test
        @DisplayName("Should return default max expiry seconds")
        void shouldReturnDefaultMaxExpirySeconds() {
            assertThat(validator.getMaxExpirySeconds()).isEqualTo(3600L);
        }
    }
}
