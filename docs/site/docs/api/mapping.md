# Mapping

## Table of Contents

- [AttributeMapper](#attributemapper)
- [MappingData](#mappingdata)
- [MappingOverrideMode](#mappingoverridemode)
- [MappingIssue](#mappingissue)
- [MappingException](#mappingexception)


`io.github.wphillipmoore.mq.rest.admin.mapping`

## AttributeMapper

The core mapping engine that translates between developer-friendly attribute
names and MQSC parameter names. Created internally by `MqRestSession` from
`MappingData`.

The mapper performs three types of translation in each direction:

- **Key mapping**: Attribute name translation
- **Value mapping**: Enumerated value translation
- **Key-value mapping**: Combined name+value translation

## MappingData

Holds the complete mapping tables loaded from the JSON resource file at:

```
src/main/resources/io/github/wphillipmoore/mq/rest/admin/mapping/mapping-data.json
```

The data is organized by qualifier (e.g. `queue`, `channel`, `qmgr`) with
separate maps for request and response directions.

## MappingOverrideMode

Controls how custom overrides are merged with built-in mapping data:

- **MERGE** (default): Override entries are merged at the key level within each
  sub-map. Existing entries not mentioned in the override are preserved.
- **REPLACE**: The override completely replaces the specified sub-map.

## MappingIssue

Tracks mapping problems encountered during translation:

- Unknown attribute names (not found in key map)
- Unknown attribute values (not found in value map)
- Ambiguous mappings

In strict mode, any `MappingIssue` causes a `MappingException`. In lenient
mode, issues are collected but the unmapped values pass through.

## MappingException

`io.github.wphillipmoore.mq.rest.admin.mapping.MappingException`

Thrown when attribute mapping fails in strict mode. Contains the list of
`MappingIssue` instances that caused the failure.
