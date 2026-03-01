package com.wpanther.pdfsigning.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Kafka messaging.
 * <p>
 * Groups Kafka-related configuration including bootstrap servers
 * and topic names for saga commands, replies, notifications, and DLQ.
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    /**
     * Kafka bootstrap servers (comma-separated list of host:port).
     * Default: localhost:9092
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * Topic configuration for various Kafka topics.
     */
    private final Topics topics = new Topics();

    /**
     * Kafka topic names configuration.
     */
    @Data
    public static class Topics {

        /**
         * Topic for consuming saga commands from orchestrator.
         * Default: saga.command.pdf-signing
         */
        private String sagaCommand = "saga.command.pdf-signing";

        /**
         * Topic for consuming saga compensation commands from orchestrator.
         * Default: saga.compensation.pdf-signing
         */
        private String sagaCompensation = "saga.compensation.pdf-signing";

        /**
         * Topic for publishing saga replies to orchestrator.
         * Default: saga.reply.pdf-signing
         */
        private String sagaReply = "saga.reply.pdf-signing";

        /**
         * Topic for publishing notification events to notification-service.
         * Default: notification.events
         */
        private String notificationEvents = "notification.events";

        /**
         * Dead Letter Queue topic for failed messages.
         * Default: pdf.signing.dlq
         */
        private String dlq = "pdf.signing.dlq";
    }
}
