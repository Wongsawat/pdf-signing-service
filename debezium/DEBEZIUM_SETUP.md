# Debezium CDC Setup for pdf-signing-service

## Overview

The pdf-signing-service uses the **transactional outbox pattern** with Debezium CDC for reliable event publishing to Kafka.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ pdf-signing-service                                               │
│                                                                   │
│  @Transactional                                                │
│  sagaCommandHandler.handleProcessCommand()                    │
│      ├── domain logic (sign PDF)                               │
│      ├── repository.save(document)    ──────────┐              │
│      └── outboxService.saveWithRouting() ─────────────┐         │
│                                                    │         │      │
│                    ┌───────────────────────────────┼─────────┘      │
│                    │                                   │               │
│                    ▼                                   ▼               │
│              PostgreSQL (pdfsigning_db)                       │
│              ┌───────────────────────────────────┐               │
│              │ outbox_events table                │               │
│              │ - id, aggregate_type, aggregate_id  │               │
│              │ - event_type, payload (JSON)        │               │
│              │ - topic, partition_key, headers    │               │
│              │ - status: PENDING → PUBLISHED       │               │
│              └────────────────────────────────────┘               │
│                           │                                      │
│                           │ Debezium CDC                         │
│                           ▼ (polls WAL changes)                   │
│                    ┌──────────────────────┐                        │
│                    │ Kafka Connect       │                        │
│                    │ (Debezium runtime) │                        │
│                    └──────────┬───────────┘                        │
│                               │                                   │
│                               ▼                                   │
│                    ┌──────────────────────┐                        │
│                    │ Kafka Topics        │                        │
│                    │                    │                        │
│                    │ saga.reply.        │                        │
│                    │   pdf-signing     │ ← saga orchestrator    │
│                    │                    │                        │
│                    │ notification.events │ ← notification-service │
│                    └──────────────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

## Kafka Connect Deployment

### Option 1: Register via Kafka Connect REST API

```bash
# Register the Debezium connector
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium/connector-config.json

# Check connector status
curl http://localhost:8083/connectors/pdf-signing-outbox-connector/status

# Delete connector (if needed)
curl -X DELETE http://localhost:8083/connectors/pdf-signing-outbox-connector
```

### Option 2: Docker Compose

Add to your `docker-compose.yml`:

```yaml
version: '3.8'

services:
  zookeeper:
    image: quay.io/debezium/zookeeper:2.3
    ports:
      - "2181:2181"
      - "2888:2888"

  kafka:
    image: quay.io/debezium/kafka:2.3
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper
    environment:
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181

  kafka-connect:
    image: quay.io/debezium/connect:2.3
    ports:
      - "8083:8083"
    depends_on:
      - kafka
      - pdfsigning-db
    environment:
      - BOOTSTRAP_SERVERS=kafka:9092
      - GROUP_ID=1
      - CONFIG_STORAGE_TOPIC=my_connect_configs
      - OFFSET_STORAGE_TOPIC=my_connect_offsets
      - KEY_CONVERTER=org.apache.kafka.connect.json.JsonConverter
      - VALUE_CONVERTER=org.apache.kafka.connect.json.JsonConverter
    volumes:
      - ./debezium/connector-config.json:/tmp/connector-config.json
      - ./kafka-connect/plugins:/kafka/connect
    command:
      - /docker-entrypoint.sh
      - -Djava.io.tmpdir=/tmp

  pdfsigning-db:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=pdfsigning_db
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres

# Wait for services and register connector
```

Then register the connector:
```bash
# Start services
docker-compose up -d

# Register connector (after services are ready)
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium/connector-config.json
```

## Environment Variables

The `connector-config.json` uses environment variable placeholders that can be configured via:

1. **Environment variables** when registering the connector:
```bash
export DB_HOST=your-db-host
export DB_USERNAME=your-user
export DB_PASSWORD=your-password

curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium/connector-config.json
```

2. **Kubernetes ConfigMap**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: debezium-connector-config
data:
  connector-config.json: |
    {
      "name": "pdf-signing-outbox-connector",
      "config": {
        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
        "database.hostname": "pdfsigning-db-service",
        "database.port": "5432",
        "database.user": "${POSTGRES_USER}",
        ...
      }
    }
```

## Event Routing

The Debezium EventRouter transform routes events based on the `topic` field in `outbox_events`:

| Event Type | Topic Field | Target Topic |
|------------|------------|--------------|
| `PdfSigningReply` | `saga.reply.pdf-signing` | `saga.reply.pdf-signing` |
| `PdfSignedNotification` | `notification.events` | `notification.events` |
| `PdfSigningFailedNotification` | `notification.events` | `notification.events` |

**Headers** (JSON in `headers` column):
```json
{
  "sagaId": "...",
  "correlationId": "...",
  "status": "SUCCESS"
}
```

## Monitoring

### Check Connector Status

```bash
curl http://localhost:8083/connectors/pdf-signing-outbox-connector/status
```

Expected output:
```json
{
  "name": "pdf-signing-outbox-connector",
  "connector": {
    "state": "RUNNING",
    "worker_id": "..."
  },
  "tasks": [
    {
      "id": "0",
      "state": "RUNNING"
    }
  ]
}
```

### Check Lag

```bash
curl http://localhost:8083/connectors/pdf-signing-outbox-connector/metrics
```

## Troubleshooting

### Issue: Events not appearing in Kafka

1. Check connector is running:
   ```bash
   curl http://localhost:8083/connectors/pdf-signing-outbox-connector/status
   ```

2. Check outbox table for events:
   ```sql
   SELECT * FROM outbox_events WHERE status = 'PENDING';
   ```

3. Check connector logs for errors

4. Verify `topic` field is set correctly in outbox records

### Issue: Duplicate events

Debezium CDC guarantees at-least-once delivery. Ensure consumers are idempotent by using the `eventId` field for deduplication.

### Issue: Wrong topic routing

Verify the `topic` field in `outbox_events` matches the expected topic pattern:
- `saga.reply.pdf-signing`
- `notification.events`

## References

- [Debezium PostgreSQL Connector](https://debezium.io/documentation/connectors/postgresql/)
- [Debezium EventRouter SMT](https://debezium.io/documentation/transforms/event-router)
- [Outbox Pattern](https://debezium.io/documentation/reference/event-flavors.html#outbox-pattern)
