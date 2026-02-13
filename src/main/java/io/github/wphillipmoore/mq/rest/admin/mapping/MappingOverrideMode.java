package io.github.wphillipmoore.mq.rest.admin.mapping;

/**
 * Strategy for applying mapping data overrides.
 *
 * <p>Controls how user-provided mapping overrides interact with the built-in mapping data loaded
 * from the default resource file.
 */
public enum MappingOverrideMode {

  /** Sparse overlay — override entries layer on top of built-in data. */
  MERGE,

  /** Complete replacement — override must cover all base keys. */
  REPLACE
}
