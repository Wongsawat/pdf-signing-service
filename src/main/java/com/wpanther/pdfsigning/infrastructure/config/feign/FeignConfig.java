package com.wpanther.pdfsigning.infrastructure.config.feign;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.MDC;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Feign client configuration with circuit breaker support.
 *
 * Provides:
 * - Request/response logging
 * - Retry mechanism
 * - Circuit breaker integration
 * - Custom error decoder
 */
@Configuration
public class FeignConfig {

    /**
     * Configures Feign logging level.
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Configures Feign request options.
     * Sets connection and read timeouts.
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                5000, TimeUnit.MILLISECONDS,   // Connect timeout
                30000, TimeUnit.MILLISECONDS,  // Read timeout
                true                            // Follow redirects
        );
    }

    /**
     * Registers custom error decoder for CSC API errors.
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CSCErrorDecoder();
    }

    /**
     * Request interceptor for distributed tracing.
     * Adds X-Request-ID header to all outgoing Feign requests.
     */
    @Bean
    public RequestInterceptor requestIdInterceptor() {
        return template -> {
            // Try to get existing request ID from MDC (if set by logging framework)
            String requestId = MDC.get("X-Request-ID");

            // If not in MDC, generate a new one
            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
            }

            // Add X-Request-ID header
            template.header("X-Request-ID", requestId);

            // Also add correlation ID if available
            String correlationId = MDC.get("X-Correlation-ID");
            if (correlationId != null) {
                template.header("X-Correlation-ID", correlationId);
            }
        };
    }

    /**
     * Customizes the Resilience4J circuit breaker factory.
     *
     * <p>Named circuit breaker instances (csc-auth, csc-sign-hash) are configured
     * in application.yml via resilience4j.circuitbreaker.instances and
     * resilience4j.timelimiter.instances. This default customizer applies only
     * to the unnamed "default" instance, which is not used by this service.
     * Returning null defers to application.yml for the named instances.</p>
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer(
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {

        return factory -> factory.configureDefault(id -> null);
    }
}
