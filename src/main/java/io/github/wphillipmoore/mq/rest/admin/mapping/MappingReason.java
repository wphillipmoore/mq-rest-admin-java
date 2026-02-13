package io.github.wphillipmoore.mq.rest.admin.mapping;

/**
 * Category of attribute mapping failure.
 *
 * <p>Mirrors pymqrest's {@code MappingReason} literal type.
 */
public enum MappingReason {
  UNKNOWN_KEY,
  UNKNOWN_VALUE,
  UNKNOWN_QUALIFIER
}
