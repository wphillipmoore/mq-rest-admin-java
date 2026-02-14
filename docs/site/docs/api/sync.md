# Sync

## Table of Contents

- [SyncConfig](#syncconfig)
- [SyncResult](#syncresult)
- [SyncOperation](#syncoperation)
- [Usage](#usage)


`io.github.wphillipmoore.mq.rest.admin.sync`

## SyncConfig

Configuration for a bulk sync operation:

```java
SyncConfig config = SyncConfig.builder()
    .qualifier("QLOCAL")
    .desiredState(desiredQueues)
    .deleteExtras(false)
    .nameFilter("APP.*")
    .build();
```

| Method | Type | Description |
| --- | --- | --- |
| `qualifier(String)` | Required | MQSC qualifier to sync (e.g. `"QLOCAL"`) |
| `desiredState(List)` | Required | List of desired object definitions |
| `deleteExtras(boolean)` | Optional | Delete objects not in desired list (default: `false`) |
| `nameFilter(String)` | Optional | Pattern to limit scope of comparison |

## SyncResult

A record containing the results of a sync operation:

```java
public record SyncResult(
    int created,
    int altered,
    int unchanged,
    int deleted,
    List<SyncOperation> operations
) {}
```

## SyncOperation

Details of a single operation performed during sync:

```java
public record SyncOperation(
    String name,
    SyncAction action,
    Map<String, Object> attributes
) {}
```

## Usage

See [Sync Methods](../sync-methods.md) for usage examples.
