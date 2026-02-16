# Examples

The `examples/` directory contains practical scripts that demonstrate common
MQ administration tasks using `mq-rest-admin`. Each example is self-contained
and can be run against the local Docker environment.

## Prerequisites

Start the multi-queue-manager Docker environment and seed both queue managers:

```bash
./scripts/dev/mq_start.sh
./scripts/dev/mq_seed.sh
```

This starts two queue managers (`QM1` on port 9453, `QM2` on port 9454) on a
shared Docker network. See [local MQ container](development/local-mq-container.md) for details.

## Health check

Connect to one or more queue managers and check:

- Queue manager attributes via `displayQmgr()`
- Running status via `displayQmstatus()`
- Listener definitions via `displayListener()`

```java
var session = MqRestSession.builder()
    .host("localhost").port(9453).queueManager("QM1")
    .credentials(new LtpaAuth("mqadmin", "mqadmin"))
    .verifyTls(false)
    .build();

var qmgr = session.displayQmgr();
System.out.println("Queue manager: " + qmgr.get("queue_manager_name"));

var status = session.displayQmstatus();
System.out.println("Status: " + status.get("channel_initiator_status"));

var listeners = session.displayListener("*");
for (var listener : listeners) {
    System.out.println("Listener: " + listener.get("listener_name")
        + " port=" + listener.get("port"));
}
```

## Queue depth monitor

Display all local queues with their current depth and flag queues
approaching capacity:

```java
var queues = session.displayQueue("*");

for (var queue : queues) {
    var depth = ((Number) queue.get("current_queue_depth")).intValue();
    var maxDepth = ((Number) queue.get("max_queue_depth")).intValue();
    var pct = maxDepth > 0 ? (depth * 100 / maxDepth) : 0;
    var flag = pct > 80 ? " *** HIGH ***" : "";
    System.out.printf("%-40s %5d / %5d (%d%%)%s%n",
        queue.get("queue_name"), depth, maxDepth, pct, flag);
}
```

## Channel status report

Cross-reference channel definitions with live channel status:

```java
var channels = session.displayChannel("*");
var statuses = session.displayChstatus("*");

var runningChannels = statuses.stream()
    .map(s -> s.get("channel_name"))
    .collect(Collectors.toSet());

for (var channel : channels) {
    var name = channel.get("channel_name");
    var state = runningChannels.contains(name) ? "RUNNING" : "INACTIVE";
    System.out.println(name + ": " + state);
}
```

## Environment provisioner

Demonstrate bulk provisioning across two queue managers using ensure
methods:

```java
// Ensure application queues exist on QM1
session.ensureQlocal("APP.REQUESTS", Map.of(
    "max_queue_depth", 50000,
    "default_persistence", "persistent"
));
session.ensureQlocal("APP.RESPONSES", Map.of(
    "max_queue_depth", 50000,
    "default_persistence", "persistent"
));

// Ensure listeners are running
var config = new SyncConfig(60, 1);
session.startListenerSync("TCP.LISTENER", config);

System.out.println("Environment provisioned");
```

## Dead letter queue inspector

Inspect the dead letter queue configuration:

```java
var qmgr = session.displayQmgr();
var dlqName = (String) qmgr.get("dead_letter_q_name");

if (dlqName != null && !dlqName.isBlank()) {
    var dlq = session.displayQueue(dlqName);
    if (!dlq.isEmpty()) {
        var depth = dlq.get(0).get("current_queue_depth");
        var maxDepth = dlq.get(0).get("max_queue_depth");
        System.out.println("DLQ: " + dlqName
            + " depth=" + depth + " max=" + maxDepth);
    }
} else {
    System.out.println("No dead letter queue configured");
}
```
