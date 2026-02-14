# Transport

## Table of Contents

- [MqRestTransport](#mqresttransport)
- [HttpClientTransport](#httpclienttransport)
- [TransportResponse](#transportresponse)
- [Custom transport](#custom-transport)

`io.github.wphillipmoore.mq.rest.admin.MqRestTransport`

## MqRestTransport

The transport interface defines the HTTP layer used by `MqRestSession`:

```java
public interface MqRestTransport {
    TransportResponse postJson(
        String url,
        Map<String, Object> payload,
        Map<String, String> headers,
        Duration timeout,
        SSLContext sslContext
    );
}
```

## HttpClientTransport

The default transport implementation using `java.net.http.HttpClient` (JDK
built-in, zero additional dependencies):

```java
var transport = new HttpClientTransport();
```

`HttpClientTransport` handles:

- HTTPS connections with configurable `SSLContext`
- Request timeouts via `Duration`
- JSON serialization/deserialization with Gson
- Custom HTTP headers

## TransportResponse

A record containing the HTTP response data:

```java
public record TransportResponse(
    int statusCode,
    String body,
    Map<String, List<String>> headers
) {}
```

## Custom transport

Implement `MqRestTransport` to provide custom HTTP behavior or for testing:

```java
// Mock transport for unit tests
MqRestTransport mockTransport = (url, payload, headers, timeout, ssl) -> {
    return new TransportResponse(200, responseJson, Map.of());
};

var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .transport(mockTransport)
    .build();
```
