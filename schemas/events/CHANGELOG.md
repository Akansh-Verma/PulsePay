# PulsePay Event Schema Changelog

All schema changes are documented here. Consumers MUST check `metadata.eventVersion`
and reject or dead-letter any version they have not been coded to handle.

## Compatibility rules

- **BACKWARD compatible** (safe): adding an optional field, widening an enum.
- **BREAKING** (requires version bump + coordinated deploy): removing a field,
  making an optional field required, narrowing an enum, renaming a field,
  changing a field's type.

When a breaking change is made: bump `eventVersion`, add a new entry below,
and update all producers before deploying consumers.

---

## event-metadata

| Version | Date | Change |
|---------|------|--------|
| 1 | 2026-03-21 | Initial schema. Fields: eventId, eventType, eventVersion, occurredAt, traceId, correlationId, producerService, tenantId. |
| 2 | 2026-03-21 | Added optional: schemaId, environment. No breaking changes. |

---

## payment-created

| Version | Date | Change |
|---------|------|--------|
| 1 | 2026-03-21 | Initial schema. Rails: UPI, CARD, WALLET, BNPL. |
| 2 | 2026-03-21 | Added rails: NEFT, IMPS. Added optional: failureCode, failureReason. No breaking changes. |

---

## wallet-event

| Version | Date | Change |
|---------|------|--------|
| 1 | 2026-03-21 | Initial schema. paymentId was required. entryTypes: DEBIT, CREDIT, REVERSAL. |
| 2 | 2026-03-21 | paymentId made optional (supports top-ups). Added entryType: TOPUP. Added optional: merchantId, feeAmount. BREAKING: consumers must handle missing paymentId. |

---

## fraud-alert

| Version | Date | Change |
|---------|------|--------|
| 1 | 2026-03-21 | Initial schema. Decisions: ALLOW, REVIEW, BLOCK. ruleHits.weight optional. riskScore optional. |
| 2 | 2026-03-21 | Added decision: CHALLENGE. riskScore made required. ruleHits.weight made required. Added optional: accountId. BREAKING: riskScore and weight are now required; producers must be updated before consumers. |

---

## notification-request

| Version | Date | Change |
|---------|------|--------|
| 1 | 2026-03-21 | Initial schema. Channels: PUSH, SMS, EMAIL, WHATSAPP. |
| 2 | 2026-03-21 | Added channel: IN_APP. Added optional: expiresAt, retryPolicy. No breaking changes. |

---

## audit-event

| Version | Date | Change |
|---------|------|--------|
| 1 | 2026-03-21 | Initial schema. |
| 2 | 2026-03-21 | Added optional: ipAddress, sessionId. No breaking changes. |

---

## dead-letter-event

| Version | Date | Change |
|---------|------|--------|
| 1 | 2026-03-21 | Initial schema. New file — no prior version. |
