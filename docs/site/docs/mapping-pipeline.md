# Mapping Pipeline

## The three-namespace problem

--8<-- "mapping-pipeline/three-namespace-problem.md"

In Java, developer-friendly names use `snake_case` (e.g. `current_queue_depth`,
`default_persistence`), matching the convention across the mq-rest-admin library
family. The mapping tables are loaded from a JSON resource file and are identical
across language implementations.

```java
// With mapping enabled (default) — developer-friendly names
var queues = session.displayQueue("MY.QUEUE",
    Map.of("response_parameters", List.of("current_queue_depth", "max_queue_depth")));
// Returns: [{"queue_name": "MY.QUEUE", "current_queue_depth": 0, "max_queue_depth": 5000}]

// With mapping disabled — native MQSC names
var queues = session.displayQueue("MY.QUEUE",
    Map.of("response_parameters", List.of("CURDEPTH", "MAXDEPTH")),
    Map.of("mapAttributes", false));
// Returns: [{"queue": "MY.QUEUE", "curdepth": 0, "maxdepth": 5000}]
```

## Qualifier-based mapping

--8<-- "mapping-pipeline/qualifier-based-mapping.md"

See the [Qualifier Mapping Reference](mappings/index.md) for the complete
per-qualifier documentation, including every key map, value map, and key-value
map entry.

## Request mapping flow

--8<-- "mapping-pipeline/request-mapping-flow.md"

### Java example

```java
// Developer-friendly request parameters
session.defineQlocal("MY.QUEUE", Map.of(
    "max_queue_depth", 50000,
    "default_persistence", "yes",
    "description", "Application queue"
));

// After request mapping, the JSON payload sent to MQ contains:
// { "MAXDEPTH": 50000, "DEFPSIST": "YES", "DESCR": "Application queue" }
```

## Response mapping flow

--8<-- "mapping-pipeline/response-mapping-flow.md"

### Java example

```java
// Request specific response attributes using developer-friendly names
var queues = session.displayQueue("MY.QUEUE",
    Map.of("response_parameters", List.of("current_queue_depth", "max_queue_depth")));

// MQ returns MQSC names in the response:
// { "queue": "MY.QUEUE", "curdepth": 0, "maxdepth": 5000 }

// After response mapping, the caller receives developer-friendly names:
// { "queue_name": "MY.QUEUE", "current_queue_depth": 0, "max_queue_depth": 5000 }
```

## Strict vs lenient mode

--8<-- "mapping-pipeline/strict-vs-lenient.md"

In Java, the mode is set at session construction via the builder:

```java
// Strict mode (default) — throws MappingException on unknown attributes
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .mappingStrict(true)  // default
    .build();

// Lenient mode — unknown attributes pass through unchanged
var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .mappingStrict(false)
    .build();
```

## Custom mapping overrides

--8<-- "mapping-pipeline/custom-mapping-overrides.md"

### Java example

Overrides are passed as a nested `Map` to the session builder. You only specify
the entries you want to change — all other mappings remain intact:

```java
var overrides = Map.of(
    "qualifiers", Map.of(
        "queue", Map.of(
            "response_key_map", Map.of(
                "CURDEPTH", "queue_depth",      // override built-in mapping
                "MAXDEPTH", "queue_max_depth"    // override built-in mapping
            )
        )
    )
);

var session = MqRestSession.builder()
    .host("localhost")
    .port(9443)
    .queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .mappingOverrides(overrides)
    .build();

var queues = session.displayQueue("MY.QUEUE");
// Returns: [{"queue_depth": 0, "queue_max_depth": 5000, ...}]
// All other queue attributes keep their default names
```

Invalid override structures raise exceptions at session construction time, so
errors are caught before any commands are sent.

## Per-call opt-out

Mapping can be disabled for a single command invocation without changing the
session-level setting:

```java
// Session has mapping enabled (default)
var session = MqRestSession.builder()
    .host("localhost").port(9443).queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .build();

// This call uses native MQSC names
var queues = session.displayQueue("MY.QUEUE",
    Map.of("response_parameters", List.of("CURDEPTH", "MAXDEPTH")),
    Map.of("mapAttributes", false));

// Returns native MQSC names: [{"queue": "MY.QUEUE", "curdepth": 0, ...}]
```

Mapping can also be disabled entirely at the session level:

```java
var session = MqRestSession.builder()
    .host("localhost").port(9443).queueManager("QM1")
    .credentials(new BasicAuth("admin", "passw0rd"))
    .mapAttributes(false)  // all commands use native MQSC names
    .build();
```
