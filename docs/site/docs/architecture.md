# Architecture

## Component overview

--8<-- "architecture/component-overview.md"

In the Java implementation, the core components are:

- **`MqRestSession`**: The main entry point, equivalent to a single connection
  to a queue manager's REST API.
- **Command methods**: Generated methods on `MqRestSession` (e.g.
  `displayQueue()`, `defineQlocal()`, `deleteChannel()`).
- **`AttributeMapper`**: Handles bidirectional attribute translation using
  `MappingData` loaded from a JSON resource file.
- **Exception hierarchy**: Sealed exception classes rooted at `MqRestException`.

## Request lifecycle

--8<-- "architecture/request-lifecycle.md"

## Transport abstraction

--8<-- "architecture/transport-abstraction.md"

In Java, the transport is defined by the `MqRestTransport` interface with a
default `HttpClientTransport` implementation using `java.net.http.HttpClient`.
The interface accepts `Duration` for timeouts and `SSLContext` for TLS/mTLS
configuration.

## Single-endpoint design

--8<-- "architecture/single-endpoint-design.md"

## Gateway routing

--8<-- "architecture/gateway-routing.md"

## Package structure

```
io.github.wphillipmoore.mq.rest.admin
    MqRestSession, MqRestTransport, HttpClientTransport, TransportResponse
io.github.wphillipmoore.mq.rest.admin.auth
    Credentials, BasicAuth, LtpaAuth, CertificateAuth
io.github.wphillipmoore.mq.rest.admin.exception
    MqRestException, MqRest{Transport,Response,Auth,Command,Timeout}Exception
io.github.wphillipmoore.mq.rest.admin.mapping
    AttributeMapper, MappingData, MappingIssue, MappingException, MappingOverrideMode
io.github.wphillipmoore.mq.rest.admin.sync
    SyncConfig, SyncResult, SyncOperation
io.github.wphillipmoore.mq.rest.admin.ensure
    EnsureResult, EnsureAction
```
