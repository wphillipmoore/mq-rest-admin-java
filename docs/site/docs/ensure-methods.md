# Declarative Object Management

## Table of Contents

- [Java usage](#java-usage)

--8<-- "concepts/ensure-pattern.md"

## Java usage

### EnsureResult and EnsureAction

```java
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureAction;
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureResult;

// EnsureAction enum: CREATED, UPDATED, UNCHANGED
// EnsureResult record: action + changed attribute names
```

### Basic usage

```java
// First call — queue does not exist yet
EnsureResult result = session.ensureQlocal(
    "APP.REQUEST.Q",
    Map.of(
        "max_q_depth", 50000,
        "description", "Application request queue"
    )
);
assert result.action() == EnsureAction.CREATED;

// Second call — same attributes, nothing to change
result = session.ensureQlocal(
    "APP.REQUEST.Q",
    Map.of(
        "max_q_depth", 50000,
        "description", "Application request queue"
    )
);
assert result.action() == EnsureAction.UNCHANGED;

// Third call — description changed, only that attribute is altered
result = session.ensureQlocal(
    "APP.REQUEST.Q",
    Map.of(
        "max_q_depth", 50000,
        "description", "Updated request queue"
    )
);
assert result.action() == EnsureAction.UPDATED;
assert result.changed().contains("description");
```

### Queue manager ensure

```java
EnsureResult result = session.ensureQmgr(Map.of(
    "statistics_queue", "on",
    "statistics_channel", "on",
    "monitoring_queue", "medium",
    "monitoring_channel", "medium"
));
// result.action() is UPDATED or UNCHANGED, never CREATED
```

### Java method signatures

Most ensure methods share the same signature:

```java
EnsureResult ensureQlocal(String name, Map<String, Object> requestParameters);
```

The queue manager variant has no name parameter:

```java
EnsureResult ensureQmgr(Map<String, Object> requestParameters);
```

### Configuration management example

The ensure pattern is designed for scripts that declare desired state:

```java
void configureQueueManager(MqRestSession session) {
    // Ensure queue manager settings
    EnsureResult result = session.ensureQmgr(Map.of(
        "statistics_queue", "on",
        "statistics_channel", "on",
        "monitoring_queue", "medium",
        "monitoring_channel", "medium"
    ));
    System.out.println("Queue manager: " + result.action());

    // Ensure application queues
    var queues = Map.of(
        "APP.REQUEST.Q", Map.of("max_q_depth", 50000, "def_persistence", "yes"),
        "APP.REPLY.Q", Map.of("max_q_depth", 10000, "def_persistence", "no"),
        "APP.DLQ", Map.of("max_q_depth", 100000, "def_persistence", "yes")
    );

    for (var entry : queues.entrySet()) {
        result = session.ensureQlocal(entry.getKey(), entry.getValue());
        System.out.println(entry.getKey() + ": " + result.action());
    }
}
```

Running this method repeatedly produces no side effects when the configuration
is already correct. Only genuine changes trigger `ALTER` commands, keeping
`ALTDATE`/`ALTTIME` accurate.
