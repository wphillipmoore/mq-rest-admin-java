package io.github.wphillipmoore.mq.rest.admin.ensure;

/**
 * The action taken by an ensure operation.
 *
 * <p>Mirrors pymqrest's {@code EnsureAction} enum.
 */
public enum EnsureAction {

  /** The object was newly created (DEFINE). */
  CREATED,

  /** The object existed but was altered because attributes differed. */
  UPDATED,

  /** The object existed and all attributes already matched. */
  UNCHANGED
}
