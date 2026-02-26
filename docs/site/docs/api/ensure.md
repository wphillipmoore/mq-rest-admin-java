# Ensure

## The problem with ALTER

Every `alter*()` call sends an `ALTER` command to the queue manager,
even when every specified attribute already matches the current state.
MQ updates `ALTDATE` and `ALTTIME` on every `ALTER`, regardless of
whether any values actually changed. This makes `ALTER` unsuitable for
declarative configuration management where idempotency matters — running
the same configuration twice should not corrupt audit timestamps.

## The ensure pattern

The `ensure*()` methods implement a declarative upsert pattern:

1. **DEFINE** the object when it does not exist.
2. **ALTER** only the attributes that differ from the current state.
3. **Do nothing** when all specified attributes already match,
   preserving `ALTDATE` and `ALTTIME`.

## EnsureAction

An enum indicating the action taken by an ensure method:

```java
public enum EnsureAction {
    CREATED,    // Object did not exist; DEFINE was issued
    UPDATED,    // Object existed but attributes differed; ALTER was issued
    UNCHANGED   // Object already matched the desired state
}
```

## EnsureResult

A record containing the action taken and the list of attribute names that
triggered the change (if any):

```java
public record EnsureResult(
    EnsureAction action,
    List<String> changed    // attribute names that differed (empty for CREATED/UNCHANGED)
) {}
```

| Method | Return type | Description |
| --- | --- | --- |
| `action()` | `EnsureAction` | What happened: `CREATED`, `UPDATED`, or `UNCHANGED` |
| `changed()` | `List<String>` | Attribute names that triggered an ALTER (in the caller's namespace) |

## Method signature patterns

Most methods share the same signature:

```java
EnsureResult ensureQlocal(String name, Map<String, Object> requestParameters);
```

The queue manager ensure method omits the name parameter:

```java
EnsureResult ensureQmgr(Map<String, Object> requestParameters);
```

`responseParameters` is not exposed — the ensure logic always requests
`["all"]` internally so it can compare the full current state.

## Basic usage

```java
// First call — queue does not exist yet
EnsureResult result = session.ensureQlocal(
    "APP.REQUEST.Q",
    Map.of(
        "max_queue_depth", 50000,
        "description", "Application request queue"
    )
);
assert result.action() == EnsureAction.CREATED;

// Second call — same attributes, nothing to change
result = session.ensureQlocal(
    "APP.REQUEST.Q",
    Map.of(
        "max_queue_depth", 50000,
        "description", "Application request queue"
    )
);
assert result.action() == EnsureAction.UNCHANGED;

// Third call — description changed, only that attribute is altered
result = session.ensureQlocal(
    "APP.REQUEST.Q",
    Map.of(
        "max_queue_depth", 50000,
        "description", "Updated request queue"
    )
);
assert result.action() == EnsureAction.UPDATED;
assert result.changed().contains("description");
```

## Comparison logic

The ensure methods compare only the attributes the caller passes in
`requestParameters` against the current state returned by `DISPLAY`.
Attributes not specified by the caller are ignored.

Comparison is:

- **Case-insensitive** — `"ENABLED"` matches `"enabled"`.
- **Type-normalizing** — integer `5000` matches string `"5000"`.
- **Whitespace-trimming** — `" YES "` matches `"YES"`.

An attribute present in `requestParameters` but absent from the
`DISPLAY` response is treated as changed and included in the `ALTER`.

## Selective ALTER

When an update is needed, only the changed attributes are sent in the
`ALTER` command. Attributes that already match are excluded from the
request. This minimizes the scope of each `ALTER` to the strict delta.

## Available methods

Each method targets a specific MQ object type with the correct
MQSC qualifier triple (DISPLAY / DEFINE / ALTER):

| Method | Object type | DISPLAY | DEFINE | ALTER |
| --- | --- | --- | --- | --- |
| `ensureQmgr()` | Queue manager | `QMGR` | — | `QMGR` |
| `ensureQlocal()` | Local queue | `QUEUE` | `QLOCAL` | `QLOCAL` |
| `ensureQremote()` | Remote queue | `QUEUE` | `QREMOTE` | `QREMOTE` |
| `ensureQalias()` | Alias queue | `QUEUE` | `QALIAS` | `QALIAS` |
| `ensureQmodel()` | Model queue | `QUEUE` | `QMODEL` | `QMODEL` |
| `ensureChannel()` | Channel | `CHANNEL` | `CHANNEL` | `CHANNEL` |
| `ensureAuthinfo()` | Auth info | `AUTHINFO` | `AUTHINFO` | `AUTHINFO` |
| `ensureListener()` | Listener | `LISTENER` | `LISTENER` | `LISTENER` |
| `ensureNamelist()` | Namelist | `NAMELIST` | `NAMELIST` | `NAMELIST` |
| `ensureProcess()` | Process | `PROCESS` | `PROCESS` | `PROCESS` |
| `ensureService()` | Service | `SERVICE` | `SERVICE` | `SERVICE` |
| `ensureTopic()` | Topic | `TOPIC` | `TOPIC` | `TOPIC` |
| `ensureSub()` | Subscription | `SUB` | `SUB` | `SUB` |
| `ensureStgclass()` | Storage class | `STGCLASS` | `STGCLASS` | `STGCLASS` |
| `ensureComminfo()` | Comm info | `COMMINFO` | `COMMINFO` | `COMMINFO` |
| `ensureCfstruct()` | CF structure | `CFSTRUCT` | `CFSTRUCT` | `CFSTRUCT` |

### Queue manager (singleton)

`ensureQmgr()` has no `name` parameter because the queue manager is a
singleton that always exists. It can only return `UPDATED` or
`UNCHANGED` (never `CREATED`):

This makes it ideal for asserting queue manager-level settings such as
statistics, monitoring, events, and logging attributes without
corrupting `ALTDATE`/`ALTTIME` on every run.

## Attribute mapping

The ensure methods participate in the same
[mapping pipeline](mapping-pipeline.md) as all other command methods.
Pass `snake_case` attribute names in `requestParameters` and the
mapping layer translates them to MQSC names for the DISPLAY, DEFINE,
and ALTER commands automatically.

## Configuration management example

The ensure pattern is designed for scripts that declare desired state:

```java
void configureQueueManager(MqRestSession session) {
    // Ensure queue manager settings
    EnsureResult result = session.ensureQmgr(Map.of(
        "queue_statistics", "on",
        "channel_statistics", "on",
        "queue_monitoring", "medium",
        "channel_monitoring", "medium"
    ));
    System.out.println("Queue manager: " + result.action());

    // Ensure application queues
    var queues = Map.of(
        "APP.REQUEST.Q", Map.of("max_queue_depth", 50000, "default_persistence", "yes"),
        "APP.REPLY.Q", Map.of("max_queue_depth", 10000, "default_persistence", "no"),
        "APP.DLQ", Map.of("max_queue_depth", 100000, "default_persistence", "yes")
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
