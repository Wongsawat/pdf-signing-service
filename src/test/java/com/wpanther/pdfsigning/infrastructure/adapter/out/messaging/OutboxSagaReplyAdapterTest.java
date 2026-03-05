package com.wpanther.pdfsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.infrastructure.config.properties.KafkaProperties;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxSagaReplyAdapter Tests")
class OutboxSagaReplyAdapterTest {

    @Mock private OutboxService outboxService;
    @Mock private KafkaProperties kafkaProperties;
    private OutboxSagaReplyAdapter adapter;

    @BeforeEach
    void setUp() {
        KafkaProperties.Topics topics = new KafkaProperties.Topics();
        topics.setSagaReply("saga.reply.pdf-signing");
        when(kafkaProperties.getTopics()).thenReturn(topics);
        adapter = new OutboxSagaReplyAdapter(outboxService, new ObjectMapper(), kafkaProperties);
    }

    @Test
    @DisplayName("publishSuccess routes to saga.reply topic")
    void publishSuccess_routesToSagaReplyTopic() {
        adapter.publishSuccess(
            "saga-1", SagaStep.SIGN_PDF, "corr-1",
            "doc-1", "http://example.com/signed.pdf", 12345L,
            "txn-1", "PEM-CERT", "PAdES-BASELINE-B", Instant.now()
        );

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("saga.reply.pdf-signing"), eq("saga-1"), any()
        );
    }

    @Test
    @DisplayName("publishFailure routes to saga.reply topic")
    void publishFailure_routesToSagaReplyTopic() {
        adapter.publishFailure("saga-1", SagaStep.SIGN_PDF, "corr-1", "error msg");

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("saga.reply.pdf-signing"), eq("saga-1"), any()
        );
    }

    @Test
    @DisplayName("publishCompensated routes to saga.reply topic")
    void publishCompensated_routesToSagaReplyTopic() {
        adapter.publishCompensated("saga-1", SagaStep.SIGN_PDF, "corr-1");

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("saga.reply.pdf-signing"), eq("saga-1"), any()
        );
    }
}
