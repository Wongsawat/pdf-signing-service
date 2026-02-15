-- Create outbox_events table for transactional outbox pattern
-- This table stores integration events before they are published to Kafka
-- Debezium CDC streams events from this table to Kafka topics

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    topic VARCHAR(255),
    partition_key VARCHAR(255),
    headers TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for outbox queries
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_events(created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- Add comments
COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable event publishing via Debezium CDC';
COMMENT ON COLUMN outbox_events.id IS 'Unique event identifier';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Aggregate type (e.g., SignedPdfDocument)';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'Aggregate ID (e.g., document ID)';
COMMENT ON COLUMN outbox_events.event_type IS 'Event type (e.g., PdfSigningReply)';
COMMENT ON COLUMN outbox_events.payload IS 'Event payload (JSON)';
COMMENT ON COLUMN outbox_events.status IS 'PENDING, PUBLISHED, FAILED';
COMMENT ON COLUMN outbox_events.topic IS 'Target Kafka topic for CDC routing';
COMMENT ON COLUMN outbox_events.partition_key IS 'Kafka partition key';
COMMENT ON COLUMN outbox_events.headers IS 'Message headers (JSON)';
COMMENT ON COLUMN outbox_events.created_at IS 'Event creation timestamp';
COMMENT ON COLUMN outbox_events.published_at IS 'Event publication timestamp';
