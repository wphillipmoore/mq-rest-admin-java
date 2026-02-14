# Ensure

## Overview

The ensure package provides the return types for the 16 idempotent ensure
methods on `MqRestSession`. These methods implement a declarative upsert
pattern: DEFINE if the object does not exist, ALTER only attributes that differ,
or no-op if the object already matches the desired state.

## EnsureAction

An enum indicating the action taken by an ensure method:

```java
public enum EnsureAction {
    CREATED,    // Object did not exist; DEFINE was issued
    UPDATED,    // Object existed but attributes differed; ALTER was issued
    UNCHANGED   // Object already matched the desired state
}
```

## EnsureResult

A record containing the action taken and the list of attribute names that
triggered the change (if any):

```java
public record EnsureResult(
    EnsureAction action,
    List<String> changed    // attribute names that differed (empty for CREATED/UNCHANGED)
) {}
```

| Method | Return type | Description |
| --- | --- | --- |
| `action()` | `EnsureAction` | What happened: `CREATED`, `UPDATED`, or `UNCHANGED` |
| `changed()` | `List<String>` | Attribute names that triggered an ALTER (in the caller's namespace) |

## Usage

```java
EnsureResult result = session.ensureQlocal("MY.QUEUE",
    Map.of("max_queue_depth", 50000, "description", "App queue"));

switch (result.action()) {
    case CREATED   -> System.out.println("Queue created");
    case UPDATED   -> System.out.println("Changed: " + result.changed());
    case UNCHANGED -> System.out.println("Already correct");
}
```

See [Ensure Methods](../ensure-methods.md) for the full conceptual overview,
comparison logic, and the complete list of available ensure methods.
