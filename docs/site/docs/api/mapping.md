# Mapping

## Overview

The mapping package provides bidirectional attribute translation between
developer-friendly `snake_case` names and native MQSC parameter names. The
mapper is created internally by `MqRestSession` from `MappingData` and is not
typically used directly.

See [Mapping Pipeline](../mapping-pipeline.md) for a conceptual overview of
how mapping works.

## AttributeMapper

The core mapping engine. Translates attribute names and values between the
developer-friendly namespace and the MQSC namespace. The mapper performs three
types of translation in each direction:

- **Key mapping**: Attribute name translation (e.g. `current_queue_depth` ↔
  `CURDEPTH`)
- **Value mapping**: Enumerated value translation (e.g. `"yes"` ↔ `"YES"`,
  `"server_connection"` ↔ `"SVRCONN"`)
- **Key-value mapping**: Combined name+value translation for cases where both
  key and value change together (e.g. `channel_type="server_connection"` →
  `CHLTYPE("SVRCONN")`)

The mapper is qualifier-aware: it selects the correct mapping tables based on
the MQSC command's qualifier (e.g. `queue`, `channel`, `qmgr`).

## MappingData

Holds the complete mapping tables loaded from the JSON resource file at:

```text
src/main/resources/io/github/wphillipmoore/mq/rest/admin/mapping/mapping-data.json
```

The data is organized by qualifier (e.g. `queue`, `channel`, `qmgr`) with
separate maps for request and response directions. Each qualifier contains:

- `request_key_map` — developer-friendly → MQSC key mapping for requests
- `request_value_map` — value translations for request attributes
- `request_key_value_map` — combined key+value translations for requests
- `response_key_map` — MQSC → developer-friendly key mapping for responses
- `response_value_map` — value translations for response attributes

The mapping data was originally bootstrapped from IBM MQ 9.4 documentation and
covers all standard MQSC attributes across 42 qualifiers.

## MappingOverrideMode

Controls how custom overrides are merged with built-in mapping data:

```java
public enum MappingOverrideMode {
    MERGE,    // default — overlay at key level, preserve unmentioned entries
    REPLACE   // completely replace the specified sub-map
}
```

- **MERGE** (default): Override entries are merged at the key level within each
  sub-map. Existing entries not mentioned in the override are preserved. This is
  the common case for changing a few attribute names without losing the rest.
- **REPLACE**: The override completely replaces the specified sub-map. Use when
  you need full control over a qualifier's mapping.

## MappingIssue

Tracks mapping problems encountered during translation:

- Unknown attribute names (not found in key map)
- Unknown attribute values (not found in value map)
- Ambiguous mappings

In strict mode, any `MappingIssue` causes a `MappingException`. In lenient
mode, issues are collected but the unmapped values pass through unchanged.

## MappingException

`io.github.wphillipmoore.mq.rest.admin.mapping.MappingException`

Thrown when attribute mapping fails in strict mode. Separate from the
`MqRestException` hierarchy (it does not extend `MqRestException`). Contains
the list of `MappingIssue` instances that caused the failure.

```java
try {
    session.displayQueue("MY.QUEUE",
        Map.of("response_parameters", List.of("invalid_attribute_name")));
} catch (MappingException e) {
    // e.getMessage() describes the unmappable attributes
}
```
