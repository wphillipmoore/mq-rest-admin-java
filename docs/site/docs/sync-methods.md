# Synchronous Start/Stop/Restart

## The problem with fire-and-forget

All MQSC `START` and `STOP` commands are fire-and-forget — they return
immediately without waiting for the object to reach its target state.
In practice, tooling that provisions infrastructure needs to wait until
a channel is `RUNNING` or a listener is `STOPPED` before proceeding to
the next step. Writing polling loops by hand is error-prone and
clutters business logic with retry mechanics.

## The sync pattern

The `*Sync` and `restart*` methods wrap the fire-and-forget commands
with a polling loop that issues `DISPLAY *STATUS` until the object
reaches a stable state or the timeout expires.

Each call returns a `SyncResult` describing what happened:

```java
import io.github.wphillipmoore.mq.rest.admin.sync.SyncConfig;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncOperation;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncResult;

// SyncOperation enum values:
//   STARTED   — Object confirmed running
//   STOPPED   — Object confirmed stopped
//   RESTARTED — Stop-then-start completed

// SyncResult record:
//   operation()      — the SyncOperation performed
//   polls()          — number of status polls issued
//   elapsedSeconds() — wall-clock time from command to confirmation
```

Polling is controlled by a `SyncConfig` record:

```java
// SyncConfig record:
//   timeoutSeconds (default 30)     — max wait before raising
//   pollIntervalSeconds (default 1) — seconds between polls
```

If the object does not reach the target state within the timeout,
`MqRestTimeoutException` is raised.

## Basic usage

```java
// Start a channel and wait until it is RUNNING
SyncResult result = session.startChannelSync("TO.PARTNER");
assert result.operation() == SyncOperation.STARTED;
System.out.println("Channel running after " + result.polls() + " poll(s), "
    + result.elapsedSeconds() + "s");

// Stop a listener and wait until it is STOPPED
result = session.stopListenerSync("TCP.LISTENER");
assert result.operation() == SyncOperation.STOPPED;
```

## Restart convenience

The `restart*` methods perform a synchronous stop followed by a
synchronous start. Each phase gets the full timeout independently —
worst case is 2x the configured timeout.

The returned `SyncResult` reports **total** polls and **total** elapsed
time across both phases:

```java
SyncResult result = session.restartChannel("TO.PARTNER");
assert result.operation() == SyncOperation.RESTARTED;
System.out.println("Restarted in " + result.elapsedSeconds() + "s ("
    + result.polls() + " total polls)");
```

## Custom timeout and poll interval

Pass a `SyncConfig` to override the defaults:

```java
// Aggressive polling for fast local development
SyncConfig fast = new SyncConfig(10, 0.25);
SyncResult result = session.startServiceSync("MY.SVC", fast);

// Patient polling for remote queue managers
SyncConfig patient = new SyncConfig(120, 5);
result = session.startChannelSync("REMOTE.CHL", patient);
```

## Timeout handling

When the timeout expires, `MqRestTimeoutException` is raised with
diagnostic attributes:

```java
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestTimeoutException;

try {
    session.startChannelSync(
        "BROKEN.CHL",
        new SyncConfig(15, 1)
    );
} catch (MqRestTimeoutException e) {
    System.out.println("Timed out: " + e.getMessage());
}
```

`MqRestTimeoutException` extends `MqRestException`, so existing
`catch (MqRestException e)` handlers will catch it.

## Available methods

| Method | Operation | START/STOP qualifier | Status qualifier |
| --- | --- | --- | --- |
| `startChannelSync()` | Start | `CHANNEL` | `CHSTATUS` |
| `stopChannelSync()` | Stop | `CHANNEL` | `CHSTATUS` |
| `restartChannel()` | Restart | `CHANNEL` | `CHSTATUS` |
| `startListenerSync()` | Start | `LISTENER` | `LSSTATUS` |
| `stopListenerSync()` | Stop | `LISTENER` | `LSSTATUS` |
| `restartListener()` | Restart | `LISTENER` | `LSSTATUS` |
| `startServiceSync()` | Start | `SERVICE` | `SVSTATUS` |
| `stopServiceSync()` | Stop | `SERVICE` | `SVSTATUS` |
| `restartService()` | Restart | `SERVICE` | `SVSTATUS` |

All methods share the same signature pattern:

```java
SyncResult startChannelSync(String name);
SyncResult startChannelSync(String name, SyncConfig config);
```

The `config` parameter is optional — when omitted, the default `SyncConfig`
(30-second timeout, 1-second poll interval) is used.

## Status detection

The polling loop checks the `STATUS` attribute in the `DISPLAY *STATUS`
response. The target values are:

- **Start**: `RUNNING`
- **Stop**: `STOPPED`

### Channel stop edge case

When a channel stops, its `CHSTATUS` record may disappear entirely
(the `DISPLAY CHSTATUS` response returns no rows). The channel sync
methods treat an empty status result as successfully stopped. Listener
and service status records are always present, so empty results are not
treated as stopped for those object types.

## Attribute mapping

The sync methods call the internal MQSC command layer, so they participate
in the same [mapping pipeline](mapping-pipeline.md) as all other
command methods. The status key is checked using both the mapped
`snake_case` name and the raw MQSC name, so polling works correctly
regardless of whether mapping is enabled or disabled.

## Provisioning example

The sync methods pair naturally with the
[ensure methods](ensure-methods.md) for end-to-end provisioning:

```java
SyncConfig config = new SyncConfig(60, 1);

// Ensure listeners exist for application and admin traffic
session.ensureListener("APP.LISTENER", Map.of(
    "transport_type", "TCP",
    "port", 1415,
    "start_mode", "MQSVC_CONTROL_Q_MGR"
));
session.ensureListener("ADMIN.LISTENER", Map.of(
    "transport_type", "TCP",
    "port", 1416,
    "start_mode", "MQSVC_CONTROL_Q_MGR"
));

// Start them synchronously
session.startListenerSync("APP.LISTENER", config);
session.startListenerSync("ADMIN.LISTENER", config);

System.out.println("Listeners ready");
```

## Rolling restart example

Restart all listeners with error handling — useful when a queue
manager serves multiple TCP ports for different client populations:

```java
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestTimeoutException;

var listeners = List.of("APP.LISTENER", "ADMIN.LISTENER", "PARTNER.LISTENER");
var config = new SyncConfig(30, 2);

for (var name : listeners) {
    try {
        SyncResult result = session.restartListener(name, config);
        System.out.println(name + ": restarted in " + result.elapsedSeconds() + "s");
    } catch (MqRestTimeoutException e) {
        System.out.println(name + ": timed out — " + e.getMessage());
    }
}
```
