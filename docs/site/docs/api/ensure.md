# Ensure

## Table of Contents

- [EnsureResult](#ensureresult)
- [EnsureAction](#ensureaction)
- [Usage](#usage)

`io.github.wphillipmoore.mq.rest.admin.ensure`

## EnsureResult

An enum indicating the action taken by an ensure method:

```java
public enum EnsureResult {
    CREATED,    // Object did not exist; DEFINE was issued
    ALTERED,    // Object existed but attributes differed; ALTER was issued
    UNCHANGED   // Object already matched the desired state
}
```

## EnsureAction

An enum representing the possible actions during ensure processing:

```java
public enum EnsureAction {
    DEFINE,     // Create a new object
    ALTER,      // Modify an existing object
    NOOP        // No action needed
}
```

## Usage

See [Ensure Methods](../ensure-methods.md) for usage examples and the full list
of available ensure methods.
