# Architecture

## Component overview

--8<-- "architecture/component-overview.md"

In the Java implementation, the core components map to these classes:

- **`MqRestSession`**: The main entry point. A single class (no mixin
  decomposition) that owns connection details, authentication, mapping
  configuration, diagnostic state, and all ~144 command methods plus 16
  ensure methods. Created via a builder pattern.
- **Command methods**: Generated methods on `MqRestSession` (e.g.
  `displayQueue()`, `defineQlocal()`, `deleteChannel()`). Each method is a
  thin wrapper that calls the internal `mqscCommand()` dispatcher with the
  correct verb and qualifier.
- **`AttributeMapper`**: Handles bidirectional attribute translation using
  `MappingData` loaded from a JSON resource file. See the
  [Mapping Pipeline](mapping-pipeline.md) for details.
- **Exception hierarchy**: Sealed exception classes rooted at `MqRestException`.
  All are unchecked (`RuntimeException`), so callers are not forced to catch
  them but can choose to handle specific subtypes.

## Request lifecycle

--8<-- "architecture/request-lifecycle.md"

In Java, the command dispatcher is the internal `mqscCommand()` method on
`MqRestSession`. Every public command method (e.g. `displayQueue()`,
`defineQlocal()`) delegates to it with the appropriate verb and qualifier.

The session retains diagnostic state from the most recent command for
inspection:

```java
session.displayQueue("MY.QUEUE");

session.getLastCommandPayload();    // the JSON sent to MQ
session.getLastResponsePayload();   // the parsed JSON response
session.getLastHttpStatus();        // HTTP status code
session.getLastResponseText();      // raw response body
```

## Transport abstraction

--8<-- "architecture/transport-abstraction.md"

In Java, the transport is defined by the `MqRestTransport` interface:

```java
public interface MqRestTransport {
    TransportResponse postJson(
        String url,
        Map<String, Object> payload,
        Map<String, String> headers,
        Duration timeout,
        boolean verifyTls
    );
}
```

The default implementation, `HttpClientTransport`, uses `java.net.http.HttpClient`
(JDK built-in). It accepts an optional `SSLContext` at construction for mTLS
client certificate authentication.

For testing, inject a mock or lambda transport:

```java
MqRestTransport mockTransport = (url, payload, headers, timeout, verify) ->
    new TransportResponse(200, responseJson, Map.of());

var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .transport(mockTransport)
    .build();
```

This makes the entire command pipeline testable without an MQ server.

## Single-endpoint design

--8<-- "architecture/single-endpoint-design.md"

In Java, this means every command method on `MqRestSession` ultimately calls the
same `postJson()` method on the transport with the same URL pattern. The only
variation is the JSON payload content.

## Gateway routing

--8<-- "architecture/gateway-routing.md"

In Java, configure gateway routing via the session builder:

```java
var session = MqRestSession.builder()
    .host("qm1-host")
    .port(9443)
    .queueManager("QM2")           // target (remote) queue manager
    .credentials(new BasicAuth("mqadmin", "mqadmin"))
    .gatewayQmgr("QM1")            // local gateway queue manager
    .build();
```

## Package structure

```text
io.github.wphillipmoore.mq.rest.admin
    MqRestSession           — Main entry point (builder, commands, ensure, diagnostics)
    MqRestTransport         — Transport interface
    HttpClientTransport     — Default transport (java.net.http.HttpClient)
    TransportResponse       — HTTP response record (statusCode, body, headers)

io.github.wphillipmoore.mq.rest.admin.auth
    Credentials             — Sealed interface
    BasicAuth               — HTTP Basic authentication
    LtpaAuth                — LTPA token-based authentication (auto-login)
    CertificateAuth         — Client certificate authentication (mTLS)

io.github.wphillipmoore.mq.rest.admin.exception
    MqRestException         — Sealed base class (RuntimeException)
    MqRestTransportException    — Network/connection failures
    MqRestResponseException     — Malformed JSON, unexpected structure
    MqRestAuthException         — Authentication/authorization failures
    MqRestCommandException      — MQSC command returned error codes
    MqRestTimeoutException      — Polling timeout exceeded

io.github.wphillipmoore.mq.rest.admin.mapping
    AttributeMapper         — Bidirectional attribute translation engine
    MappingData             — Mapping tables loaded from JSON resource
    MappingIssue            — Tracks individual mapping problems
    MappingException        — Thrown on strict-mode mapping failure
    MappingOverrideMode     — MERGE or REPLACE override strategy

io.github.wphillipmoore.mq.rest.admin.ensure
    EnsureAction            — Enum: CREATED, UPDATED, UNCHANGED
    EnsureResult            — Record: action + changed attribute names
```
