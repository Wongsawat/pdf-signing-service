package com.wpanther.pdfsigning.infrastructure.config.feign;

import com.wpanther.pdfsigning.domain.model.SigningException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom Feign error decoder for CSC API errors.
 *
 * Translates HTTP error responses from the CSC API into domain-specific exceptions.
 */
@Slf4j
public class CSCErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String errorMessage = extractErrorMessage(response);

        log.error("CSC API error - Method: {}, Status: {}, Message: {}",
                methodKey, response.status(), errorMessage);

        return switch (response.status()) {
            case 400 -> new SigningException(
                    "Bad request to CSC API: " + errorMessage);
            case 401 -> new SigningException(
                    "Authentication failed with CSC API: " + errorMessage);
            case 403 -> new SigningException(
                    "Access denied by CSC API: " + errorMessage);
            case 404 -> new SigningException(
                    "CSC API endpoint not found: " + errorMessage);
            case 500, 502, 503, 504 -> new SigningException(
                    "CSC API server error: " + errorMessage);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }

    /**
     * Extracts error message from the response body.
     */
    private String extractErrorMessage(Response response) {
        try {
            if (response.body() != null) {
                byte[] bodyBytes = response.body().asInputStream().readAllBytes();
                return new String(bodyBytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to read error response body", e);
        }
        return "No error message available";
    }
}
