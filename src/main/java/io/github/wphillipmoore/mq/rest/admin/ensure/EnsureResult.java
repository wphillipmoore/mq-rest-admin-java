package io.github.wphillipmoore.mq.rest.admin.ensure;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Result of an ensure operation, containing the action taken and the list of attribute names that
 * differed (triggering an ALTER).
 *
 * <p>The {@code changed} list contains attribute names in the caller's namespace. It is empty for
 * {@link EnsureAction#CREATED} and {@link EnsureAction#UNCHANGED}.
 *
 * <p>Mirrors pymqrest's {@code EnsureResult} dataclass.
 */
public record EnsureResult(EnsureAction action, List<String> changed) implements Serializable {

  /**
   * Creates an ensure result.
   *
   * @param action the action taken (must not be null)
   * @param changed the attribute names that differed, or null for an empty list
   */
  public EnsureResult {
    Objects.requireNonNull(action, "action");
    changed = changed != null ? List.copyOf(changed) : List.of();
  }
}
