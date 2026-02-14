# Sync

## Overview

The sync package provides the types for the 9 synchronous start/stop/restart
methods on `MqRestSession`. These methods wrap fire-and-forget `START` and
`STOP` commands with a polling loop that waits until the object reaches its
target state or the timeout expires.

## SyncOperation

An enum indicating the operation that was performed:

```java
public enum SyncOperation {
    STARTED,    // Object confirmed running
    STOPPED,    // Object confirmed stopped
    RESTARTED   // Stop-then-start completed
}
```

## SyncConfig

A record controlling the polling behaviour:

```java
public record SyncConfig(
    double timeoutSeconds,       // Max wait before raising (default 30)
    double pollIntervalSeconds   // Seconds between polls (default 1)
) {}
```

| Method | Return type | Description |
| --- | --- | --- |
| `timeoutSeconds()` | `double` | Maximum seconds to wait before raising `MqRestTimeoutException` |
| `pollIntervalSeconds()` | `double` | Seconds between `DISPLAY *STATUS` polls |

## SyncResult

A record containing the outcome of a sync operation:

```java
public record SyncResult(
    SyncOperation operation,   // What happened: STARTED, STOPPED, or RESTARTED
    int polls,                 // Number of status polls issued
    double elapsedSeconds      // Wall-clock time from command to confirmation
) {}
```

| Method | Return type | Description |
| --- | --- | --- |
| `operation()` | `SyncOperation` | What happened: `STARTED`, `STOPPED`, or `RESTARTED` |
| `polls()` | `int` | Number of status polls issued |
| `elapsedSeconds()` | `double` | Wall-clock seconds from command to confirmation |

## Method signature pattern

All 9 sync methods follow the same signature pattern:

```java
SyncResult startChannelSync(String name);
SyncResult startChannelSync(String name, SyncConfig config);
```

## Usage

```java
SyncResult result = session.startChannelSync("TO.PARTNER");

switch (result.operation()) {
    case STARTED   -> System.out.println("Running after " + result.polls() + " polls");
    case STOPPED   -> System.out.println("Stopped");
    case RESTARTED -> System.out.println("Restarted in " + result.elapsedSeconds() + "s");
}
```

See [Sync Methods](../sync-methods.md) for the full conceptual overview,
polling behaviour, and the complete list of available methods.
