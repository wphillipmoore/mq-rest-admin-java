# Sync Methods

## Table of Contents

- [Java usage](#java-usage)


--8<-- "concepts/sync-pattern.md"

## Java usage

### Basic sync

```java
import io.github.wphillipmoore.mq.rest.admin.sync.SyncConfig;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncResult;

var desiredQueues = List.of(
    Map.of("name", "APP.REQUEST", "max_depth", 10000),
    Map.of("name", "APP.REPLY", "max_depth", 5000),
    Map.of("name", "APP.DLQ", "max_depth", 50000)
);

SyncConfig config = SyncConfig.builder()
    .qualifier("QLOCAL")
    .desiredState(desiredQueues)
    .deleteExtras(false)
    .nameFilter("APP.*")
    .build();

SyncResult result = session.sync(config);

System.out.println("Created: " + result.created());
System.out.println("Altered: " + result.altered());
System.out.println("Unchanged: " + result.unchanged());
System.out.println("Deleted: " + result.deleted());
```

### Delete extras

When `deleteExtras` is `true`, objects that exist on the queue manager but are
not in the desired state list will be deleted. Use the `nameFilter` to limit
the scope:

```java
SyncConfig config = SyncConfig.builder()
    .qualifier("QLOCAL")
    .desiredState(desiredQueues)
    .deleteExtras(true)
    .nameFilter("APP.*")  // only delete APP.* queues not in the list
    .build();
```

!!! warning
    Use `deleteExtras` with caution. Always set a `nameFilter` to avoid
    accidentally deleting system or unrelated objects.
