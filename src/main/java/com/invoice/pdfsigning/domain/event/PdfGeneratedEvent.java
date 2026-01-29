package com.invoice.pdfsigning.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published by pdf-generation-service when a PDF is generated.
 * Consumed by pdf-signing-service to trigger PDF signing.
 *
 * Topic: pdf.generated
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfGeneratedEvent extends IntegrationEvent {

    /**
     * Invoice identifier (UUID)
     */
    private String invoiceId;

    /**
     * Human-readable invoice number
     */
    private String invoiceNumber;

    /**
     * Document type (INVOICE, TAX_INVOICE, etc.)
     */
    private String documentType;

    /**
     * Document identifier from pdf-generation-service
     */
    private String documentId;

    /**
     * URL to access the generated PDF
     */
    private String documentUrl;

    /**
     * File size in bytes
     */
    private Long fileSize;

    /**
     * Whether XML is embedded in the PDF
     */
    private Boolean xmlEmbedded;
}
