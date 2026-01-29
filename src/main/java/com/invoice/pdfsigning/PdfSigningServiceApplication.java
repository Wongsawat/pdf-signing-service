package com.invoice.pdfsigning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Main application class for PDF Signing Service.
 *
 * This microservice digitally signs PDF documents using PAdES format
 * (PDF Advanced Electronic Signatures) via the CSC API v2.0.
 *
 * Architecture:
 * - Port: 8087
 * - Database: PostgreSQL (pdfsigning_db)
 * - Consumes: pdf.generated (from pdf-generation-service)
 * - Produces: pdf.signed (to document-storage-service)
 * - External API: eidasremotesigning CSC API (localhost:9000)
 *
 * Event Flow:
 * pdf-generation-service → pdf.generated → pdf-signing-service → pdf.signed → document-storage-service
 *
 * @author Claude Code
 * @since 2025-01-07
 */
@SpringBootApplication
@EnableFeignClients
public class PdfSigningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfSigningServiceApplication.class, args);
    }
}
