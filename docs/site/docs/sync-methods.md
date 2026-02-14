# Synchronous Start/Stop/Restart

--8<-- "concepts/sync-pattern.md"

## Java usage

### SyncConfig, SyncOperation, and SyncResult

```java
import io.github.wphillipmoore.mq.rest.admin.sync.SyncConfig;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncOperation;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncResult;

// SyncOperation enum: STARTED, STOPPED, RESTARTED
// SyncConfig record: timeoutSeconds (default 30), pollIntervalSeconds (default 1)
// SyncResult record: operation, polls, elapsedSeconds
```

### Basic start and stop

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

### Restart convenience

```java
SyncResult result = session.restartChannel("TO.PARTNER");
assert result.operation() == SyncOperation.RESTARTED;
System.out.println("Restarted in " + result.elapsedSeconds() + "s ("
    + result.polls() + " total polls)");
```

### Custom timeout and poll interval

Pass a `SyncConfig` to override the defaults:

```java
// Aggressive polling for fast local development
SyncConfig fast = new SyncConfig(10, 0.25);
SyncResult result = session.startServiceSync("MY.SVC", fast);

// Patient polling for remote queue managers
SyncConfig patient = new SyncConfig(120, 5);
result = session.startChannelSync("REMOTE.CHL", patient);
```

### Timeout handling

When the timeout expires, `MqRestTimeoutException` is raised:

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

### Available methods

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

### Provisioning example

The sync methods pair naturally with the [ensure methods](ensure-methods.md)
for end-to-end provisioning:

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
