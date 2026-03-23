-- Migrate signature_timestamp from TIMESTAMP (timezone-naive) to TIMESTAMPTZ.
-- Existing values are interpreted as UTC (the only timezone that was ever used,
-- via ZoneOffset.UTC in the application layer).
ALTER TABLE signed_pdf_documents
    ALTER COLUMN signature_timestamp TYPE TIMESTAMPTZ
        USING signature_timestamp AT TIME ZONE 'UTC';
