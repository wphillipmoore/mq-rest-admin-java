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
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Creating a session

```java
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;

var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .build();
```

## Running a command

```java
// Display all local queues
var queues = session.displayQueue("*");
for (var queue : queues) {
    System.out.println(queue.get("queue_name"));
}
```

## Attribute mapping

By default, the session maps attribute names between Java-friendly `camelCase`
names and MQSC parameter names:

```java
// Using mapped names
var params = Map.of("max_depth", 10000, "description", "My queue");
session.defineQlocal("MY.QUEUE", params);

// Disable mapping for raw MQSC names
var rawParams = Map.of("MAXDEPTH", 10000, "DESCR", "My queue");
session.defineQlocal("MY.QUEUE", rawParams, Map.of("mapAttributes", false));
```

## Error handling

```java
import io.github.wphillipmoore.mq.rest.admin.exception.*;

try {
    session.displayQueue("NONEXISTENT.QUEUE");
} catch (MqRestCommandException e) {
    System.out.println("Reason code: " + e.getReasonCode());
} catch (MqRestTransportException e) {
    System.out.println("Connection error: " + e.getMessage());
}
```

## Next steps

- [Architecture](architecture.md) — Understand how the library is organized
- [Mapping Pipeline](mapping-pipeline.md) — Learn about attribute translation
- [API Reference](api/index.md) — Browse the full API
- [Ensure Methods](ensure-methods.md) — Declarative object management
