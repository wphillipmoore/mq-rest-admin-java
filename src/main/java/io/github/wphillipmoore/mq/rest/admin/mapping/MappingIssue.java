package io.github.wphillipmoore.mq.rest.admin.mapping;

import java.io.Serializable;
import java.util.Objects;

/**
 * Single mapping issue recorded during attribute translation.
 *
 * <p>Mirrors pymqrest's {@code MappingIssue} frozen dataclass. Each issue describes one attribute
 * that could not be fully mapped (unknown key, unknown value, or unknown qualifier).
 *
 * @param direction whether the issue occurred during request or response mapping, never null
 * @param reason category of the mapping failure, never null
 * @param attributeName the attribute name that triggered the issue, never null
 * @param attributeValue the attribute value, if relevant to the issue
 * @param objectIndex zero-based index within a response list, or null for single-object operations
 * @param qualifier the qualifier being mapped (e.g., "queue"), or null if not applicable
 */
public record MappingIssue(
    MappingDirection direction,
    MappingReason reason,
    String attributeName,
    Object attributeValue,
    Integer objectIndex,
    String qualifier)
    implements Serializable {

  /** Validates that required fields are non-null. */
  public MappingIssue {
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(attributeName, "attributeName");
  }
}
