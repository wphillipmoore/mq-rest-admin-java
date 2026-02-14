# Authentication

## Table of Contents

- [Credentials](#credentials)
- [BasicAuth](#basicauth)
- [LtpaAuth](#ltpaauth)
- [CertificateAuth](#certificateauth)


`io.github.wphillipmoore.mq.rest.admin.auth`

## Credentials

`Credentials` is a sealed interface representing authentication credentials for
the MQ REST API:

```java
public sealed interface Credentials
    permits BasicAuth, LtpaAuth, CertificateAuth {}
```

## BasicAuth

HTTP Basic authentication with username and password:

```java
var creds = new BasicAuth("admin", "passw0rd");
```

The `Authorization` header is constructed automatically from the username and
password.

## LtpaAuth

LTPA token-based authentication:

```java
var creds = new LtpaAuth("LtpaToken2=...");
```

The LTPA token is sent as a cookie header.

## CertificateAuth

Client certificate authentication via TLS mutual authentication:

```java
var sslContext = SSLContext.getInstance("TLS");
// configure with client certificate keystore...

var creds = new CertificateAuth();
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(creds)
    .sslContext(sslContext)
    .build();
```

No `Authorization` header is sent; authentication is handled at the TLS layer.
