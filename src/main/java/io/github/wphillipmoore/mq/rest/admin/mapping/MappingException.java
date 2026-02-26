package io.github.wphillipmoore.mq.rest.admin.mapping;

import java.util.List;
import java.util.Objects;

/**
 * Raised when attribute mapping fails in strict mode.
 *
 * <p>Mirrors pymqrest's {@code MappingError}. Contains one or more {@link MappingIssue} instances
 * describing exactly which attributes could not be mapped and why.
 *
 * <p>This exception extends {@link RuntimeException} directly, <em>not</em> {@code
 * MqRestException}, because it represents a data-transformation error rather than a REST API error.
 */
public final class MappingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final List<MappingIssue> issues;

  /**
   * Creates a mapping exception with an auto-generated message.
   *
   * @param issues the mapping issues that caused the failure, must not be null or empty
   */
  public MappingException(List<MappingIssue> issues) {
    super(buildMessage(validateIssues(issues)));
    this.issues = List.copyOf(issues);
  }

  /**
   * Creates a mapping exception with an explicit message.
   *
   * @param message the detail message
   * @param issues the mapping issues that caused the failure, must not be null or empty
   */
  public MappingException(String message, List<MappingIssue> issues) {
    super(message);
    this.issues = List.copyOf(validateIssues(issues));
  }

  /**
   * Returns the mapping issues that caused this exception.
   *
   * @return unmodifiable list of issues, never empty
   */
  public List<MappingIssue> getIssues() {
    return issues;
  }

  private static List<MappingIssue> validateIssues(List<MappingIssue> issues) {
    Objects.requireNonNull(issues, "issues");
    if (issues.isEmpty()) {
      throw new IllegalArgumentException("issues must not be empty");
    }
    return issues;
  }

  private static String buildMessage(List<MappingIssue> issues) {
    StringBuilder sb = new StringBuilder(64 + issues.size() * 128);
    sb.append("Mapping failed with ").append(issues.size()).append(" issue(s):");
    for (MappingIssue issue : issues) {
      sb.append("\nindex=")
          .append(issue.objectIndex() != null ? issue.objectIndex() : "-")
          .append(" | qualifier=")
          .append(issue.qualifier() != null ? issue.qualifier() : "-")
          .append(" | direction=")
          .append(issue.direction())
          .append(" | reason=")
          .append(issue.reason())
          .append(" | attribute=")
          .append(issue.attributeName())
          .append(" | value=")
          .append(issue.attributeValue() != null ? issue.attributeValue() : "-");
    }
    return sb.toString();
  }
}
