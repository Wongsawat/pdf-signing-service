package com.wpanther.pdfsigning.infrastructure.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
     * Configures Feign retry behavior.
     * Retries up to 3 times with 1 second intervals.
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(1000, 1000, 3);
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
     * Customizes the Resilience4J circuit breaker factory.
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer(
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {

        return factory -> factory.configureDefault(id -> {
            // Circuit breaker and time limiter are configured in application.yml
            return null;
        });
    }
}
