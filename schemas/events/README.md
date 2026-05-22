# PulsePay Event Schemas

JSON Schema (Draft 2020-12) contracts for all Kafka event types in PulsePay.
All schemas use a shared `metadata` envelope and a domain-specific `payload`.

See [CHANGELOG.md](./CHANGELOG.md) for version history and breaking change log.

## Files

| File | Topic | Event title | Current version |
|------|-------|-------------|-----------------|
| `event-metadata.schema.json` | — (shared envelope) | PulsePayEventMetadata | v2 |
| `payment-created.schema.json` | `payment.events` | PaymentCreatedEvent | v2 |
| `wallet-event.schema.json` | `wallet.events` | WalletEvent | v2 |
| `fraud-alert.schema.json` | `fraud.alerts` | FraudAlertEvent | v2 |
| `notification-request.schema.json` | `notification.requests` | NotificationRequestEvent | v2 |
| `audit-event.schema.json` | `audit.events` | AuditEvent | v2 |
| `dead-letter-event.schema.json` | `<topic>.dlq` | DeadLetterEvent | v1 |

## Top-level structure

Every event validates this shape:
```json
{
  "metadata": { },
  "payload":  { }
}
```

## DLQ topics

Each Kafka topic has a corresponding dead-letter topic:

| Source topic | DLQ topic |
|---|---|
| `payment.events` | `payment.events.dlq` |
| `wallet.events` | `wallet.events.dlq` |
| `fraud.alerts` | `fraud.alerts.dlq` |
| `notification.requests` | `notification.requests.dlq` |
| `audit.events` | `audit.events.dlq` |

Failed messages are wrapped in `DeadLetterEvent` before publishing to the DLQ topic.

## Schema evolution rules

- Consumers MUST read `metadata.eventVersion` before processing.
- Unknown enum values MUST be treated as `UNKNOWN` — do not throw.
- Unknown object fields MUST be ignored (read-lenient, write-strict).
- Any breaking change requires a version bump. See CHANGELOG.md.

## Validation example (Node + Ajv)
```bash
npm i -D ajv ajv-formats
```
```js
import Ajv from "ajv";
import addFormats from "ajv-formats";
import metadataSchema from "./event-metadata.schema.json" assert { type: "json" };
import paymentSchema from "./payment-created.schema.json" assert { type: "json" };

const ajv = new Ajv({ allErrors: true, strict: false });
addFormats(ajv);

// Register the shared metadata schema by its $id so $ref resolves at runtime
ajv.addSchema(metadataSchema);
const validate = ajv.compile(paymentSchema);

const event = {
  metadata: {
    eventId: "evt_01",
    eventType: "PaymentCreated",
    eventVersion: 2,
    occurredAt: "2026-03-21T10:15:30Z",
    traceId: "trace_abc123",
    producerService: "payment-service"
  },
  payload: {
    paymentId: "pay_001",
    userId: "usr_001",
    merchantId: "mrc_001",
    amount: 100.0,
    currency: "INR",
    rail: "UPI",
    status: "INITIATED",
    idempotencyKey: "idem_001"
  }
};

if (!validate(event)) {
  console.error(validate.errors);
}
```

## Notes

- Schemas are write-strict (`additionalProperties: false`) — producers must not send unknown fields.
- Consumers must be read-lenient — ignore fields they do not recognise rather than throwing.
- `$ref` paths are file-relative for local validation. When registered with Confluent Schema
  Registry, reference by `$id` URI instead of relative path.
- `metadata.schemaId` is populated automatically by `kafka-lib`'s producer base class.
