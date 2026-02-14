# Authentication

## Overview

The authentication module provides credential types for the three
authentication modes supported by the IBM MQ REST API: mutual TLS (mTLS)
client certificates, LTPA token, and HTTP Basic.

Pass a credential object to `MqRestSession.builder()` via the `credentials()`
method. Always use TLS (`https://`) for production deployments to protect
credentials and data in transit.

```java
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.*;

// mTLS client certificate auth — strongest; no shared secrets
var session = MqRestSession.builder()
    .host("mq-host").port(9443).queueManager("QM1")
    .credentials(new CertificateAuth("/path/to/cert.pem", "/path/to/key.pem"))
    .build();

// LTPA token auth — credentials sent once at login, then cookie-based
var session = MqRestSession.builder()
    .host("mq-host").port(9443).queueManager("QM1")
    .credentials(new LtpaAuth("user", "pass"))
    .build();

// Basic auth — credentials sent with every request
var session = MqRestSession.builder()
    .host("mq-host").port(9443).queueManager("QM1")
    .credentials(new BasicAuth("user", "pass"))
    .build();
```

## Credentials

`Credentials` is a sealed interface representing authentication credentials for
the MQ REST API:

```java
public sealed interface Credentials
    permits BasicAuth, LtpaAuth, CertificateAuth {}
```

## CertificateAuth

Client certificate authentication via TLS mutual authentication (mTLS). This is
the strongest authentication mode — no shared secrets cross the wire.

```java
public record CertificateAuth(
    String certPath,   // path to client certificate file
    String keyPath     // path to private key file (nullable if combined)
) implements Credentials {}
```

```java
// Separate certificate and key files
var creds = new CertificateAuth("/path/to/cert.pem", "/path/to/key.pem");

// Combined cert+key file
var creds = new CertificateAuth("/path/to/combined.pem");
```

No `Authorization` header is sent; authentication is handled at the TLS layer
via the `SSLContext` configured on the transport.

## LtpaAuth

LTPA token-based authentication. Credentials are sent once during a `/login`
request at session construction; subsequent API calls carry only the LTPA
cookie.

```java
public record LtpaAuth(
    String username,
    String password
) implements Credentials {}
```

```java
var creds = new LtpaAuth("mqadmin", "passw0rd");
```

The session performs the login automatically at build time and extracts the
`LtpaToken2` cookie for subsequent requests.

## BasicAuth

HTTP Basic authentication. The `Authorization` header is constructed from the
username and password and sent with every request.

```java
public record BasicAuth(
    String username,
    String password
) implements Credentials {}
```

```java
var creds = new BasicAuth("mqadmin", "passw0rd");
```

## Choosing between LTPA and Basic authentication

Both LTPA and Basic authentication use a username and password. The key
difference is how often those credentials cross the wire.

**LTPA is the recommended choice for username/password authentication.**
Credentials are sent once during the `/login` request; subsequent API calls
carry only the LTPA cookie. This reduces credential exposure and is more
efficient for sessions that issue many commands.

**Use Basic authentication as a fallback when:**

- The mqweb configuration does not enable the `/login` endpoint (for example,
  minimal container images that only expose the REST API).
- A reverse proxy or API gateway handles authentication and forwards a Basic
  auth header; cookie-based flows may not survive the proxy.
- Single-command scripts where the login round-trip doubles the request count
  for no security benefit.
- Long-running sessions where LTPA token expiry (typically two hours) could
  cause mid-operation failures; the library does not currently re-authenticate
  automatically.
- Local development or CI against a `localhost` container, where transport
  security is not a concern.
