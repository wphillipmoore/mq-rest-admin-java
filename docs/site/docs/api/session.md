# MqRestSession

## Overview

The main entry point for interacting with an IBM MQ queue manager's
administrative REST API. A session encapsulates connection details,
authentication, attribute mapping configuration, and diagnostic state. It
provides ~144 command methods covering all MQSC verbs and qualifiers, plus 16
idempotent ensure methods for declarative object management.

Unlike the Python implementation which decomposes functionality across mixins,
the Java session is a single class with all methods directly available.

## Creating a session

Use the builder pattern:

```java
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.LtpaAuth;

var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new LtpaAuth("admin", "passw0rd"))
    .build();
```

The builder validates all required fields and constructs the base URL, transport,
and mapping data at build time. Errors in configuration (e.g. invalid mapping
overrides) are caught immediately.

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
| `mappingOverrides(Map)` | Optional | Custom mapping overrides (sparse merge) |
| `verifyTls(boolean)` | Optional | Verify server TLS certificates (default: `true`) |
| `sslContext(SSLContext)` | Optional | Custom `SSLContext` for TLS/mTLS |
| `timeout(Duration)` | Optional | Default request timeout |
| `csrfToken(String)` | Optional | Custom CSRF token value |
| `transport(MqRestTransport)` | Optional | Custom transport implementation |

### Minimal example

```java
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new LtpaAuth("admin", "passw0rd"))
    .build();
```

### Full example

```java
var session = MqRestSession.builder()
    .host("mq-server.example.com")
    .port(9443)
    .queueManager("QM2")
    .credentials(new LtpaAuth("mqadmin", "mqadmin"))
    .gatewayQmgr("QM1")
    .mapAttributes(true)
    .mappingStrict(false)
    .mappingOverrides(overrides)
    .verifyTls(true)
    .sslContext(sslContext)
    .timeout(Duration.ofSeconds(30))
    .build();
```

## Command methods

The session provides ~144 command methods, one for each MQSC verb + qualifier
combination. See [Commands](commands.md) for the full list.

```java
// DISPLAY commands return a list of maps
List<Map<String, Object>> queues = session.displayQueue("APP.*");

// Queue manager singletons return a single map or null
Map<String, Object> qmgr = session.displayQmgr();

// Non-DISPLAY commands return void (throw on error)
session.defineQlocal("MY.QUEUE", Map.of("max_queue_depth", 50000));
session.deleteQlocal("MY.QUEUE");
```

## Ensure methods

The session provides 16 ensure methods for declarative object management. Each
method implements an idempotent upsert: DEFINE if the object does not exist,
ALTER only the attributes that differ, or no-op if already correct.

```java
EnsureResult result = session.ensureQlocal("MY.QUEUE",
    Map.of("max_queue_depth", 50000));
// result.action() is CREATED, UPDATED, or UNCHANGED
```

See [Ensure Methods](../ensure-methods.md) for detailed usage and the full list
of available ensure methods.

## Diagnostic state

The session retains the most recent request and response for inspection. This
is useful for debugging command failures or understanding what the library sent
to the MQ REST API:

```java
session.displayQueue("MY.QUEUE");

session.getLastCommandPayload();    // the JSON sent to MQ (unmodifiable Map)
session.getLastResponsePayload();   // the parsed JSON response (unmodifiable Map)
session.getLastHttpStatus();        // HTTP status code (int)
session.getLastResponseText();      // raw response body (String)
```

### Accessor methods

| Method | Return type | Description |
| --- | --- | --- |
| `getQmgrName()` | `String` | Queue manager name |
| `getGatewayQmgr()` | `String` | Gateway queue manager (or `null`) |
| `getLastHttpStatus()` | `int` | HTTP status code from last command |
| `getLastResponseText()` | `String` | Raw response body from last command |
| `getLastResponsePayload()` | `Map<String, Object>` | Parsed response (unmodifiable) |
| `getLastCommandPayload()` | `Map<String, Object>` | Command sent (unmodifiable) |

## Sync

The session provides bulk sync operations. See [Sync](sync.md) for details.
