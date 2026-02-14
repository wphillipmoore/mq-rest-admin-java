# Exceptions

## Hierarchy

All exceptions are unchecked (extend `RuntimeException`) and sealed:

```text
MqRestException (sealed, extends RuntimeException)
├── MqRestTransportException   — Network/connection failures
├── MqRestResponseException    — Malformed JSON, unexpected structure
├── MqRestAuthException        — Authentication/authorization failures
├── MqRestCommandException     — MQSC command returned error codes
└── MqRestTimeoutException     — Polling timeout exceeded

MappingException               — Attribute mapping failures (separate hierarchy)
```

Because the hierarchy is sealed, a `catch (MqRestException e)` block catches
all library exceptions, and you can use pattern matching or `instanceof` to
handle specific subtypes.

## MqRestException

The base exception class. All library exceptions extend this sealed class. It
carries the standard `message` and optional `cause` from `RuntimeException`.

## MqRestTransportException

Thrown when the HTTP request fails at the network level — connection refused,
DNS resolution failure, TLS handshake error, etc.

| Method | Return type | Description |
| --- | --- | --- |
| `getUrl()` | `String` | The URL that was being accessed |

```java
try {
    session.displayQueue("MY.QUEUE");
} catch (MqRestTransportException e) {
    System.err.println("Cannot reach: " + e.getUrl());
    System.err.println("Cause: " + e.getCause().getMessage());
}
```

## MqRestResponseException

Thrown when the HTTP request succeeds but the response cannot be parsed — invalid
JSON, missing expected fields, unexpected response structure.

| Method | Return type | Description |
| --- | --- | --- |
| `getResponseText()` | `String` | Raw response body (may be `null`) |

## MqRestAuthException

Thrown when authentication or authorization fails — invalid credentials, expired
tokens, insufficient permissions (HTTP 401/403).

| Method | Return type | Description |
| --- | --- | --- |
| `getUrl()` | `String` | The URL that was being accessed |
| `getStatusCode()` | `Integer` | HTTP status code (may be `null`) |

```java
try {
    session.displayQmgr();
} catch (MqRestAuthException e) {
    System.err.println("Auth failed: HTTP " + e.getStatusCode());
}
```

## MqRestCommandException

Thrown when the MQSC command returns a non-zero completion or reason code. This
is the most commonly caught exception — it indicates the command was delivered
to MQ but the queue manager rejected it.

| Method | Return type | Description |
| --- | --- | --- |
| `getPayload()` | `Map<String, Object>` | Full response payload (unmodifiable) |
| `getStatusCode()` | `Integer` | HTTP status code (may be `null`) |

```java
try {
    session.defineQlocal("MY.QUEUE", Map.of());
} catch (MqRestCommandException e) {
    System.err.println(e.getMessage());
    System.err.println("HTTP status: " + e.getStatusCode());
    System.err.println("Response: " + e.getPayload());
}
```

!!! note
    For DISPLAY commands with no matches, MQ returns reason code 2085
    (MQRC_UNKNOWN_OBJECT_NAME). The library treats this as an empty list
    rather than throwing an exception.

## MqRestTimeoutException

Thrown when a polling operation exceeds the configured timeout duration.

| Method | Return type | Description |
| --- | --- | --- |
| `getName()` | `String` | Resource name being polled |
| `getOperation()` | `String` | Operation being performed |
| `getElapsed()` | `double` | Elapsed time in seconds |

## MappingException

`io.github.wphillipmoore.mq.rest.admin.mapping.MappingException`

Separate from the `MqRestException` hierarchy. Thrown by the mapping layer when
strict-mode attribute translation fails. Contains the list of `MappingIssue`
instances that caused the failure.

See [Mapping](mapping.md) for details.

## Catching exceptions

Catch the base class for broad error handling, or specific subtypes for
targeted recovery:

```java
try {
    session.defineQlocal("MY.QUEUE", Map.of("max_queue_depth", 50000));
} catch (MqRestCommandException e) {
    // MQSC command failed — check reason code in payload
    System.err.println("Command failed: " + e.getMessage());
} catch (MqRestAuthException e) {
    // Credentials rejected
    System.err.println("Not authorized: " + e.getStatusCode());
} catch (MqRestTransportException e) {
    // Network error
    System.err.println("Connection failed to " + e.getUrl());
} catch (MqRestException e) {
    // Catch-all for any other library exception
    System.err.println("Unexpected error: " + e.getMessage());
}
```
