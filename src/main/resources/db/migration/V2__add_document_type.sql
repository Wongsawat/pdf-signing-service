-- Add document_type column to signed_pdf_documents table
ALTER TABLE signed_pdf_documents
ADD COLUMN document_type VARCHAR(50) DEFAULT 'INVOICE';

-- Add index for querying by document type
CREATE INDEX idx_signed_pdf_documents_document_type
ON signed_pdf_documents(document_type);

-- Update existing records (optional, sets default)
UPDATE signed_pdf_documents SET document_type = 'INVOICE' WHERE document_type IS NULL;
