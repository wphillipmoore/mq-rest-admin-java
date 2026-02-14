# MqRestSession

## Table of Contents

- [Creating a session](#creating-a-session)
- [Builder options](#builder-options)
- [Command methods](#command-methods)
- [Ensure methods](#ensure-methods)
- [Sync](#sync)


`io.github.wphillipmoore.mq.rest.admin.MqRestSession`

The main entry point for interacting with an IBM MQ queue manager's
administrative REST API. A session encapsulates connection details,
authentication, and attribute mapping configuration.

## Creating a session

Use the builder pattern:

```java
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .build();
```

## Builder options

| Method | Type | Description |
| --- | --- | --- |
| `host(String)` | Required | Hostname or IP of the MQ REST API |
| `port(int)` | Required | HTTPS port |
| `queueManager(String)` | Required | Target queue manager name |
| `credentials(Credentials)` | Required | Authentication credentials |
| `gatewayQmgr(String)` | Optional | Gateway queue manager for remote routing |
| `mapAttributes(boolean)` | Optional | Enable/disable attribute mapping (default: `true`) |
| `mappingStrict(boolean)` | Optional | Strict or lenient mapping mode (default: `true`) |
| `mappingOverrides(Map)` | Optional | Custom mapping overrides |
| `sslContext(SSLContext)` | Optional | TLS/mTLS configuration |
| `timeout(Duration)` | Optional | Default request timeout |
| `csrfToken(String)` | Optional | Custom CSRF token value |
| `transport(MqRestTransport)` | Optional | Custom transport implementation |

## Command methods

The session provides ~144 command methods. See [Commands](commands.md) for the
full list.

## Ensure methods

The session provides 16 ensure methods for declarative object management. See
[Ensure](ensure.md) for details.

## Sync

The session provides bulk sync operations. See [Sync](sync.md) for details.
