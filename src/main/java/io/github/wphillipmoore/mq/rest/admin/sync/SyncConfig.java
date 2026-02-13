package io.github.wphillipmoore.mq.rest.admin.sync;

import java.io.Serializable;

/**
 * Configuration for sync polling operations.
 *
 * <p>Controls the timeout and polling interval for sync methods that wait for an object to reach a
 * target state.
 *
 * <p>Mirrors pymqrest's {@code SyncConfig}.
 */
public record SyncConfig(double timeoutSeconds, double pollIntervalSeconds)
    implements Serializable {

  /** Default timeout in seconds (30). */
  public static final double DEFAULT_TIMEOUT_SECONDS = 30.0;

  /** Default poll interval in seconds (1). */
  public static final double DEFAULT_POLL_INTERVAL_SECONDS = 1.0;

  /**
   * Creates a sync configuration with the specified timeout and poll interval.
   *
   * @param timeoutSeconds the maximum time to wait in seconds (must be &gt; 0)
   * @param pollIntervalSeconds the interval between polls in seconds (must be &gt; 0)
   * @throws IllegalArgumentException if either value is not positive
   */
  public SyncConfig {
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be > 0");
    }
    if (pollIntervalSeconds <= 0) {
      throw new IllegalArgumentException("pollIntervalSeconds must be > 0");
    }
  }

  /** Creates a sync configuration with default values (30s timeout, 1s poll interval). */
  public SyncConfig() {
    this(DEFAULT_TIMEOUT_SECONDS, DEFAULT_POLL_INTERVAL_SECONDS);
  }
}
