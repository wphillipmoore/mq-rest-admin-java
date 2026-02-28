# Examples

Runnable example classes demonstrate common MQ administration tasks using
`mq-rest-admin`. Each example is a standalone class with a `main()` method
that can be run against the local Docker environment.

**Location:** [`src/test/java/.../examples/`](https://github.com/wphillipmoore/mq-rest-admin-java/tree/main/src/test/java/io/github/wphillipmoore/mq/rest/admin/examples)

Examples live in the test source tree so they compile alongside integration
tests without affecting library coverage metrics. Each class is fully
self-contained with its own `main()` entry point.

## Prerequisites

Start the multi-queue-manager Docker environment and seed both queue managers:

```bash
./scripts/dev/mq_start.sh
./scripts/dev/mq_seed.sh
```

This starts two queue managers (`QM1` on port 9453, `QM2` on port 9454) on a
shared Docker network. See [local MQ container](development/local-mq-container.md) for details.

## Environment variables

| Variable               | Default                                | Description                   |
|------------------------|----------------------------------------|-------------------------------|
| `MQ_REST_BASE_URL`     | `https://localhost:9453/ibmmq/rest/v2` | QM1 REST endpoint             |
| `MQ_REST_BASE_URL_QM2` | `https://localhost:9454/ibmmq/rest/v2` | QM2 REST endpoint             |
| `MQ_QMGR_NAME`        | `QM1`                                  | Queue manager name            |
| `MQ_ADMIN_USER`        | `mqadmin`                              | Admin username                |
| `MQ_ADMIN_PASSWORD`    | `mqadmin`                              | Admin password                |
| `DEPTH_THRESHOLD_PCT`  | `80`                                   | Queue depth warning threshold |

## Health check

Connects to one or more queue managers and checks QMGR status,
command server availability, and listener state. Produces a pass/fail
summary for each queue manager.

See [`HealthCheck.java`](https://github.com/wphillipmoore/mq-rest-admin-java/blob/main/src/test/java/io/github/wphillipmoore/mq/rest/admin/examples/HealthCheck.java).

## Queue depth monitor

Displays local queues with their current depth, flags queues
approaching capacity, and sorts by depth percentage.

See [`QueueDepthMonitor.java`](https://github.com/wphillipmoore/mq-rest-admin-java/blob/main/src/test/java/io/github/wphillipmoore/mq/rest/admin/examples/QueueDepthMonitor.java).

## Channel status report

Displays channel definitions alongside live channel status, identifies
channels that are defined but not running, and shows connection details.

See [`ChannelStatus.java`](https://github.com/wphillipmoore/mq-rest-admin-java/blob/main/src/test/java/io/github/wphillipmoore/mq/rest/admin/examples/ChannelStatus.java).

## Environment provisioner

Defines a complete set of queues, channels, and remote queue definitions
across two queue managers, then verifies connectivity. Includes teardown.

See [`ProvisionEnvironment.java`](https://github.com/wphillipmoore/mq-rest-admin-java/blob/main/src/test/java/io/github/wphillipmoore/mq/rest/admin/examples/ProvisionEnvironment.java).

## Dead letter queue inspector

Checks the dead letter queue configuration, reports depth and capacity,
and suggests actions when messages are present.

See [`DlqInspector.java`](https://github.com/wphillipmoore/mq-rest-admin-java/blob/main/src/test/java/io/github/wphillipmoore/mq/rest/admin/examples/DlqInspector.java).

## Queue status and connection handles

Demonstrates `DISPLAY QSTATUS TYPE(HANDLE)` and `DISPLAY CONN TYPE(HANDLE)`
queries, showing how `mq-rest-admin` flattens nested object response
structures into uniform flat maps.

See [`QueueStatus.java`](https://github.com/wphillipmoore/mq-rest-admin-java/blob/main/src/test/java/io/github/wphillipmoore/mq/rest/admin/examples/QueueStatus.java).
