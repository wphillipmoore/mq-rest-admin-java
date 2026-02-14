# Exceptions

`io.github.wphillipmoore.mq.rest.admin.exception`

## Hierarchy

All exceptions are unchecked (extend `RuntimeException`) and sealed:

```
MqRestException (sealed, extends RuntimeException)
├── MqRestTransportException   — Network/connection failures
├── MqRestResponseException    — Malformed JSON, unexpected structure
├── MqRestAuthException        — Authentication/authorization failures
├── MqRestCommandException     — MQSC command returned error codes
└── MqRestTimeoutException     — Polling timeout exceeded
```

## MqRestException

The base exception class. All library exceptions extend this sealed class.

## MqRestTransportException

Thrown when the HTTP request fails at the network level — connection refused,
DNS resolution failure, TLS handshake error, etc.

## MqRestResponseException

Thrown when the HTTP request succeeds but the response cannot be parsed — invalid
JSON, missing expected fields, unexpected response structure.

## MqRestAuthException

Thrown when authentication or authorization fails — invalid credentials, expired
tokens, insufficient permissions (HTTP 401/403).

## MqRestCommandException

Thrown when the MQSC command returns a non-zero completion or reason code. The
exception provides:

- `getCompletionCode()` — Overall or per-item completion code
- `getReasonCode()` — MQ reason code (e.g. 2085 for MQRC_UNKNOWN_OBJECT_NAME)
- `getCommandResponse()` — The full response payload for diagnostics

!!! note
    For DISPLAY commands with no matches, MQ returns reason code 2085. The
    library treats this as an empty list rather than throwing an exception.

## MqRestTimeoutException

Thrown when a polling operation (e.g. waiting for a command to complete) exceeds
the configured timeout duration.

## MappingException

Separate from the `MqRestException` hierarchy. Thrown by the mapping layer when
strict-mode attribute translation fails. See [Mapping](mapping.md) for details.
