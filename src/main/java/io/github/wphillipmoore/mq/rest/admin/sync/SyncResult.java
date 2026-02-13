package io.github.wphillipmoore.mq.rest.admin.sync;

import java.io.Serializable;
import java.util.Objects;

/**
 * Result of a sync operation, containing the operation performed, the number of polling cycles, and
 * the elapsed time.
 *
 * <p>Mirrors pymqrest's {@code SyncResult}.
 */
public record SyncResult(SyncOperation operation, int polls, double elapsedSeconds)
    implements Serializable {

  /**
   * Creates a sync result.
   *
   * @param operation the operation that was performed (must not be null)
   * @param polls the number of polling cycles (must be &gt;= 0)
   * @param elapsedSeconds the elapsed time in seconds (must be &gt;= 0)
   * @throws NullPointerException if operation is null
   * @throws IllegalArgumentException if polls or elapsedSeconds is negative
   */
  public SyncResult {
    Objects.requireNonNull(operation, "operation");
    if (polls < 0) {
      throw new IllegalArgumentException("polls must be >= 0");
    }
    if (elapsedSeconds < 0) {
      throw new IllegalArgumentException("elapsedSeconds must be >= 0");
    }
  }
}
