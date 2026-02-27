package com.wpanther.pdfsigning.infrastructure.adapter.primary.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.pdfsigning.application.port.SagaCommandPort;
import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for {@link SagaCommandKafkaAdapter}.
 * <p>
 * Tests the primary Kafka adapter using mocked SagaCommandPort.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaCommandKafkaAdapter Tests")
class SagaCommandKafkaAdapterTest {

    @Mock
    private SagaCommandPort mockSagaCommandPort;

    private ObjectMapper objectMapper;
    private SagaCommandKafkaAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        adapter = new SagaCommandKafkaAdapter(mockSagaCommandPort, objectMapper);
    }

    @Nested
    @DisplayName("handlePdfSigningCommand() method")
    class HandlePdfSigningCommandMethod {

        @Test
        @DisplayName("Should process valid PDF signing command")
        void shouldProcessValidCommand() throws Exception {
            // Given
            ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
                "saga-123", SagaStep.SIGN_PDF, "corr-123",
                "doc-123", "INV-001", "TAX_INVOICE",
                "http://example.com/test.pdf", 1024L, false
            );

            String commandJson = objectMapper.writeValueAsString(command);

            // When
            adapter.handlePdfSigningCommand(commandJson);

            // Then
            verify(mockSagaCommandPort).handleProcessPdfSigning(any(ProcessPdfSigningCommand.class));
        }

        @Test
        @DisplayName("Should throw RuntimeException on invalid JSON")
        void shouldThrowOnInvalidJson() {
            // Given
            String invalidJson = "{invalid json";

            // When/Then
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adapter.handlePdfSigningCommand(invalidJson)
            )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process saga command");
        }

        @Test
        @DisplayName("Should propagate port exceptions as RuntimeException")
        void shouldPropagatePortException() throws Exception {
            // Given
            ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
                "saga-123", SagaStep.SIGN_PDF, "corr-123",
                "doc-123", "INV-001", "TAX_INVOICE",
                "http://example.com/test.pdf", 1024L, false
            );

            String commandJson = objectMapper.writeValueAsString(command);
            doThrow(new RuntimeException("Processing failed"))
                .when(mockSagaCommandPort).handleProcessPdfSigning(any());

            // When/Then
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adapter.handlePdfSigningCommand(commandJson)
            )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process saga command");
        }
    }

    @Nested
    @DisplayName("handleCompensationCommand() method")
    class HandleCompensationCommandMethod {

        @Test
        @DisplayName("Should process valid compensation command")
        void shouldProcessValidCommand() throws Exception {
            // Given
            CompensatePdfSigningCommand command = new CompensatePdfSigningCommand(
                "saga-123", SagaStep.SIGN_PDF, "corr-123",
                "doc-123", "TAX_INVOICE", "sign-pdf"
            );

            String commandJson = objectMapper.writeValueAsString(command);

            // When
            adapter.handleCompensationCommand(commandJson);

            // Then
            verify(mockSagaCommandPort).handleCompensatePdfSigning(any(CompensatePdfSigningCommand.class));
        }

        @Test
        @DisplayName("Should throw RuntimeException on invalid JSON")
        void shouldThrowOnInvalidJson() {
            // Given
            String invalidJson = "{invalid json";

            // When/Then
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adapter.handleCompensationCommand(invalidJson)
            )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process compensation command");
        }

        @Test
        @DisplayName("Should propagate port exceptions as RuntimeException")
        void shouldPropagatePortException() throws Exception {
            // Given
            CompensatePdfSigningCommand command = new CompensatePdfSigningCommand(
                "saga-123", SagaStep.SIGN_PDF, "corr-123",
                "doc-123", "TAX_INVOICE", "sign-pdf"
            );

            String commandJson = objectMapper.writeValueAsString(command);
            doThrow(new RuntimeException("Compensation failed"))
                .when(mockSagaCommandPort).handleCompensatePdfSigning(any());

            // When/Then
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adapter.handleCompensationCommand(commandJson)
            )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process compensation command");
        }
    }
}
