package com.wpanther.pdfsigning.domain.port.in;

import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;

/**
 * Port for saga command handling.
 * <p>
 * Infrastructure adapters (Kafka listeners) call this port to drive
 * the application. The application provides the implementation.
 * </p>
 * <p>
 * This is a "driver" port - inbound from infrastructure to the application.
 * </p>
 */
public interface SagaCommandPort {

    /**
     * Handle a process PDF signing command from the saga orchestrator.
     *
     * @param command The command containing signing details
     */
    void handleProcessPdfSigning(ProcessPdfSigningCommand command);

    /**
     * Handle a compensate PDF signing command from the saga orchestrator.
     *
     * @param command The command containing details for rollback
     */
    void handleCompensatePdfSigning(CompensatePdfSigningCommand command);
}
