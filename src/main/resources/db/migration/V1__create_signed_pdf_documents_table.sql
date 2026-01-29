-- Create signed_pdf_documents table
CREATE TABLE signed_pdf_documents (
    id UUID PRIMARY KEY,
    invoice_id VARCHAR(100) NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    original_pdf_url VARCHAR(500) NOT NULL,
    original_pdf_size BIGINT NOT NULL,
    signed_pdf_path VARCHAR(500),
    signed_pdf_url VARCHAR(500),
    signed_pdf_size BIGINT,
    transaction_id VARCHAR(100),
    certificate TEXT,
    signature_level VARCHAR(50),
    signature_timestamp TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(100)
);

-- Create unique index on invoice_id for idempotency
CREATE UNIQUE INDEX idx_signed_pdf_invoice_id ON signed_pdf_documents(invoice_id);

-- Create index on status for queries
CREATE INDEX idx_signed_pdf_status ON signed_pdf_documents(status);

-- Create index on invoice_number for lookups
CREATE INDEX idx_signed_pdf_invoice_number ON signed_pdf_documents(invoice_number);

-- Create index on created_at for time-based queries
CREATE INDEX idx_signed_pdf_created_at ON signed_pdf_documents(created_at);

-- Add comment to table
COMMENT ON TABLE signed_pdf_documents IS 'Stores signed PDF document metadata and signing status';

-- Add comments to key columns
COMMENT ON COLUMN signed_pdf_documents.id IS 'Primary key (UUID)';
COMMENT ON COLUMN signed_pdf_documents.invoice_id IS 'Reference to invoice (unique for idempotency)';
COMMENT ON COLUMN signed_pdf_documents.invoice_number IS 'Human-readable invoice identifier';
COMMENT ON COLUMN signed_pdf_documents.original_pdf_url IS 'URL of the unsigned PDF from pdf-generation-service';
COMMENT ON COLUMN signed_pdf_documents.signed_pdf_path IS 'Filesystem path to the signed PDF';
COMMENT ON COLUMN signed_pdf_documents.signed_pdf_url IS 'Public URL to access the signed PDF';
COMMENT ON COLUMN signed_pdf_documents.transaction_id IS 'CSC API transaction ID';
COMMENT ON COLUMN signed_pdf_documents.certificate IS 'PEM-encoded signing certificate';
COMMENT ON COLUMN signed_pdf_documents.signature_level IS 'Signature level (PAdES-BASELINE-T)';
COMMENT ON COLUMN signed_pdf_documents.signature_timestamp IS 'Timestamp from CSC signing service';
COMMENT ON COLUMN signed_pdf_documents.status IS 'Signing status: PENDING, SIGNING, COMPLETED, FAILED';
COMMENT ON COLUMN signed_pdf_documents.retry_count IS 'Number of retry attempts for failed signings';
COMMENT ON COLUMN signed_pdf_documents.correlation_id IS 'Correlation ID for event tracing';
