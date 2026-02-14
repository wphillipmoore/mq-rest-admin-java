# Ensure Methods

## Table of Contents

- [Java usage](#java-usage)

--8<-- "concepts/ensure-pattern.md"

## Java usage

### Basic ensure

```java
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureResult;

// Ensure a local queue exists with specific attributes
EnsureResult result = session.ensureQlocal("MY.QUEUE", Map.of(
    "max_depth", 10000,
    "description", "Application queue"
));

switch (result) {
    case CREATED -> System.out.println("Queue created");
    case ALTERED -> System.out.println("Queue updated");
    case UNCHANGED -> System.out.println("Queue already correct");
}
```

### Available ensure methods

| Method | Object type |
| --- | --- |
| `ensureQlocal()` | Local queue |
| `ensureQremote()` | Remote queue |
| `ensureQalias()` | Alias queue |
| `ensureQmodel()` | Model queue |
| `ensureChannel()` | Channel |
| `ensureSvrconn()` | Server-connection channel |
| `ensureTopic()` | Topic |
| `ensureAuthinfo()` | Authentication info |
| `ensureListener()` | Listener |
| `ensureNamelist()` | Namelist |
| `ensureProcess()` | Process |
| `ensureService()` | Service |
| `ensureComminfo()` | Communication info |
| `ensureStgclass()` | Storage class |
| `ensureSub()` | Subscription |
| `ensureQmgr()` | Queue manager (alter only) |

### Queue manager ensure

The queue manager is a singleton that always exists. `ensureQmgr()` only
supports ALTER (or no-op) and never issues DEFINE:

```java
EnsureResult result = session.ensureQmgr(Map.of(
    "description", "Production queue manager",
    "max_handles", 1024
));
// result is ALTERED or UNCHANGED, never CREATED
```
