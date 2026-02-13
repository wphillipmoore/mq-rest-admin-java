package io.github.wphillipmoore.mq.rest.admin.sync;

/**
 * The operation performed by a sync method.
 *
 * <p>Mirrors pymqrest's sync operation results.
 */
public enum SyncOperation {

  /** The object was started. */
  STARTED,

  /** The object was stopped. */
  STOPPED,

  /** The object was stopped then started. */
  RESTARTED
}
