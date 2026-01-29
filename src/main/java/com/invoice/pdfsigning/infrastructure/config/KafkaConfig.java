package com.invoice.pdfsigning.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Kafka configuration for pdf-signing-service.
 *
 * Enables Kafka support and relies on Spring Boot autoconfiguration
 * from application.yml for all Kafka settings.
 *
 * Key configuration in application.yml:
 * - Manual acknowledgment mode (ack-mode: manual)
 * - Idempotent producer (enable.idempotence: true)
 * - Consumer group: pdf-signing-service
 */
@Configuration
@EnableKafka
public class KafkaConfig {
    // All Kafka configuration is in application.yml
    // This class just enables Kafka support
}
