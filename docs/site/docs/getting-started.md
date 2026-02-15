# Getting Started

## Prerequisites

- **Java**: 17 or later
- **Maven**: 3.9+ (or use the included Maven Wrapper `./mvnw`)
- **IBM MQ**: A running queue manager with the administrative REST API enabled

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.wphillipmoore</groupId>
    <artifactId>mq-rest-admin</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Creating a session

All interaction with IBM MQ goes through an `MqRestSession`. You need the
REST API host, port, queue manager name, and credentials:

```java
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;

var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("mqadmin", "mqadmin"))
    .verifyTls(false)  // for local development only
    .build();
```

## Running a command

Every MQSC command has a corresponding method on the session. Method names
follow the pattern `verbQualifier` in camelCase:

```java
// DISPLAY QUEUE — returns a list of maps
List<Map<String, Object>> queues = session.displayQueue("SYSTEM.*");

for (var queue : queues) {
    System.out.println(queue.get("queue_name") + " " + queue.get("current_queue_depth"));
}
```

```java
// DISPLAY QMGR — returns a single map or null
Map<String, Object> qmgr = session.displayQmgr();
if (qmgr != null) {
    System.out.println(qmgr.get("queue_manager_name"));
}
```

## Attribute mapping

By default, the session maps between developer-friendly names and MQSC
parameter names. This applies to both request and response attributes:

```java
// With mapping enabled (default)
var queues = session.displayQueue("MY.QUEUE",
    Map.of("response_parameters", List.of("current_queue_depth", "max_queue_depth")));
// Returns: [{"queue_name": "MY.QUEUE", "current_queue_depth": 0, "max_queue_depth": 5000}]

// With mapping disabled
var queues = session.displayQueue("MY.QUEUE",
    Map.of("response_parameters", List.of("CURDEPTH", "MAXDEPTH")),
    Map.of("mapAttributes", false));
// Returns: [{"queue": "MY.QUEUE", "curdepth": 0, "maxdepth": 5000}]
```

Mapping can be disabled at the session level:

```java
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("mqadmin", "mqadmin"))
    .mapAttributes(false)
    .build();
```

See [Mapping Pipeline](mapping-pipeline.md) for a detailed explanation of how
mapping works.

## Strict vs lenient mapping

By default, mapping runs in strict mode. Unknown attribute names or values
raise a `MappingException`. In lenient mode, unknown attributes pass through
unchanged:

```java
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("mqadmin", "mqadmin"))
    .mappingStrict(false)
    .build();
```

## Custom mapping overrides

Sites with existing naming conventions can override individual entries in the
built-in mapping tables without replacing them entirely. Pass a
`mappingOverrides` map when creating the session:

```java
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("mqadmin", "mqadmin"))
    .mappingOverrides(Map.of(
        "qualifiers", Map.of(
            "queue", Map.of(
                "response_key_map", Map.of(
                    "CURDEPTH", "queue_depth",      // override built-in mapping
                    "MAXDEPTH", "queue_max_depth"    // override built-in mapping
                )
            )
        )
    ))
    .build();

var queues = session.displayQueue("MY.QUEUE");
// Returns: [{"queue_depth": 0, "queue_max_depth": 5000, ...}]
```

Overrides are **sparse** — you only specify the entries you want to change. All
other mappings in the qualifier continue to work as normal. In the example above,
only `CURDEPTH` and `MAXDEPTH` are remapped; every other queue attribute keeps
its default name.

Request-side mappings work the same way:

```java
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("mqadmin", "mqadmin"))
    .verifyTls(false)
    .mappingOverrides(Map.of(
        "qualifiers", Map.of(
            "queue", Map.of(
                "request_key_map", Map.of(
                    "queue_depth", "CURDEPTH"      // use your name on the request side
                ),
                "response_key_map", Map.of(
                    "CURDEPTH", "queue_depth"      // and on the response side
                )
            )
        )
    ))
    .build();

// Now "queue_depth" works in WHERE filters, response_parameters, etc.
var queues = session.displayQueue("*",
    Map.of("where", "queue_depth GT 100"));
```

Overrides support all five sub-maps per qualifier: `request_key_map`,
`request_value_map`, `request_key_value_map`, `response_key_map`, and
`response_value_map`. See [Mapping Pipeline](mapping-pipeline.md) for details
on how each sub-map is used.

Invalid override structures raise exceptions at session construction time, so
errors are caught early.

## Gateway queue manager

The MQ REST API is available on all supported IBM MQ platforms (Linux, AIX,
Windows, z/OS, and IBM i). mq-rest-admin is developed and tested against the
**Linux** implementation only.

In enterprise environments, a **gateway queue manager** can route MQSC
commands to remote queue managers via MQ channels — the same mechanism used
by `runmqsc -w` and the MQ Console.

To route commands to a remote queue manager through a gateway, pass
`gatewayQmgr` when creating the session. The `queueManager` parameter
specifies the **target** (remote) queue manager, while `gatewayQmgr` names
the **local** queue manager whose REST API routes the command:

```java
// Route commands to QM2 through QM1's REST API
var session = MqRestSession.builder()
    .host("qm1-host")
    .port(9443)
    .queueManager("QM2")           // target queue manager
    .credentials(new BasicAuth("mqadmin", "mqadmin"))
    .gatewayQmgr("QM1")            // local gateway queue manager
    .build();

var qmgr = session.displayQmgr();
// Returns QM2's queue manager attributes, routed through QM1
```

Prerequisites:

- The gateway queue manager must have a running REST API.
- MQ channels must be configured between the gateway and target queue managers.
- A QM alias (QREMOTE with empty RNAME) must map the target QM name to the
  correct transmission queue on the gateway.

## Error handling

`DISPLAY` commands return an empty list when no objects match. Queue manager
display methods return `null` when no match is found. Non-display commands
raise `MqRestCommandException` on failure:

```java
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestCommandException;

// Empty list — no exception
List<Map<String, Object>> result = session.displayQueue("NONEXISTENT.*");
assert result.isEmpty();

// Define raises on error
try {
    session.defineQlocal("MY.QUEUE", Map.of());
} catch (MqRestCommandException e) {
    System.out.println(e.getMessage());
    System.out.println("HTTP status: " + e.getStatusCode());
    System.out.println(e.getPayload());  // full MQ response payload
}
```

## Diagnostic state

The session retains the most recent request and response for inspection:

```java
session.displayQueue("MY.QUEUE");

System.out.println(session.getLastCommandPayload());    // the JSON sent to MQ
System.out.println(session.getLastResponsePayload());   // the parsed JSON response
System.out.println(session.getLastHttpStatus());        // HTTP status code
System.out.println(session.getLastResponseText());      // raw response body
```

## Next steps

- [Architecture](architecture.md) — Understand how the library is organized
- [Mapping Pipeline](mapping-pipeline.md) — Learn about attribute translation
- [API Reference](api/index.md) — Browse the full API
- [Ensure Methods](ensure-methods.md) — Declarative object management
- [Sync Methods](sync-methods.md) — Synchronous start/stop/restart
