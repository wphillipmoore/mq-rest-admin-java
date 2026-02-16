# Transport

## Overview

The transport layer abstracts HTTP communication from the session logic. The
session builds `runCommandJSON` payloads and delegates HTTP delivery to a
transport implementation. This separation enables testing the entire command
pipeline without an MQ server by injecting a mock transport.

## MqRestTransport

The transport interface defines a single method for posting JSON payloads:

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

| Parameter | Description |
| --- | --- |
| `url` | Fully-qualified endpoint URL |
| `payload` | The `runCommandJSON` request body |
| `headers` | Authentication, CSRF token, and optional gateway headers |
| `timeout` | Per-request timeout duration |
| `verifyTls` | Whether to verify server certificates |

Throws `MqRestTransportException` on network failures.

## HttpClientTransport

The default transport implementation using `java.net.http.HttpClient` (JDK
built-in, zero additional dependencies beyond Gson for JSON serialization):

```java
// Default — verifies TLS certificates
var transport = new HttpClientTransport();

// Custom SSLContext for mTLS client certificate authentication
var transport = new HttpClientTransport(sslContext);
```

`HttpClientTransport` handles:

- HTTPS connections with configurable `SSLContext`
- Automatic TLS certificate verification (or disabled via `verifyTls=false`)
- Request timeouts via `Duration`
- JSON serialization/deserialization with Gson
- Custom HTTP headers
- Defensive header flattening per RFC 9110

## TransportResponse

An immutable record containing the HTTP response data:

```java
public record TransportResponse(
    int statusCode,
    String body,                   // never null (empty string if no body)
    Map<String, String> headers    // never null, unmodifiable
) {}
```

## Custom transport

Implement `MqRestTransport` to provide custom HTTP behavior or for testing.
Because the interface has a single method, a lambda works naturally:

```java
// Mock transport for unit tests
MqRestTransport mockTransport = (url, payload, headers, timeout, verify) ->
    new TransportResponse(200, responseJson, Map.of());

var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new LtpaAuth("admin", "passw0rd"))
    .transport(mockTransport)
    .build();
```

This pattern is used extensively in the library's own test suite to verify
command payload construction, response parsing, and error handling without
network access.
