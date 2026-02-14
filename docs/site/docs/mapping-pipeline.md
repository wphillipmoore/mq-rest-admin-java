# Mapping Pipeline

## Table of Contents

- [The three-namespace problem](#the-three-namespace-problem)
- [Qualifier-based mapping](#qualifier-based-mapping)
- [Request mapping flow](#request-mapping-flow)
- [Response mapping flow](#response-mapping-flow)
- [Strict vs lenient mode](#strict-vs-lenient-mode)
- [Custom mapping overrides](#custom-mapping-overrides)


## The three-namespace problem

--8<-- "mapping-pipeline/three-namespace-problem.md"

In Java, developer-friendly names use `camelCase` (e.g. `currentDepth`,
`defaultPersistence`) rather than Python's `snake_case`, but the underlying
MQSC ↔ friendly-name translation is identical.

## Qualifier-based mapping

--8<-- "mapping-pipeline/qualifier-based-mapping.md"

See the [Qualifier Mapping Reference](mappings/index.md) for the complete
per-qualifier documentation.

## Request mapping flow

--8<-- "mapping-pipeline/request-mapping-flow.md"

## Response mapping flow

--8<-- "mapping-pipeline/response-mapping-flow.md"

## Strict vs lenient mode

--8<-- "mapping-pipeline/strict-vs-lenient.md"

## Custom mapping overrides

--8<-- "mapping-pipeline/custom-mapping-overrides.md"

### Java example

```java
var overrides = Map.of(
    "qualifiers", Map.of(
        "queue", Map.of(
            "response_key_map", Map.of(
                "CURDEPTH", "queueDepth"  // replaces the built-in mapping
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
```
