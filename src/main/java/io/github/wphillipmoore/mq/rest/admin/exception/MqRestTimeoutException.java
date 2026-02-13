package io.github.wphillipmoore.mq.rest.admin.exception;

import java.util.Objects;

/** Thrown when a polling operation exceeds its configured timeout. */
public final class MqRestTimeoutException extends MqRestException {

  private final String name;
  private final String operation;
  private final double elapsed;

  /**
   * Creates a timeout exception.
   *
   * @param message description of the failure
   * @param name the name of the resource being polled
   * @param operation the operation that timed out
   * @param elapsed the elapsed time in seconds before the timeout
   */
  public MqRestTimeoutException(String message, String name, String operation, double elapsed) {
    super(message);
    this.name = Objects.requireNonNull(name, "name");
    this.operation = Objects.requireNonNull(operation, "operation");
    this.elapsed = elapsed;
  }

  /**
   * Creates a timeout exception with a cause.
   *
   * @param message description of the failure
   * @param name the name of the resource being polled
   * @param operation the operation that timed out
   * @param elapsed the elapsed time in seconds before the timeout
   * @param cause the underlying cause
   */
  public MqRestTimeoutException(
      String message, String name, String operation, double elapsed, Throwable cause) {
    super(message, cause);
    this.name = Objects.requireNonNull(name, "name");
    this.operation = Objects.requireNonNull(operation, "operation");
    this.elapsed = elapsed;
  }

  /** Returns the name of the resource being polled. */
  public String getName() {
    return name;
  }

  /** Returns the operation that timed out. */
  public String getOperation() {
    return operation;
  }

  /** Returns the elapsed time in seconds before the timeout. */
  public double getElapsed() {
    return elapsed;
  }
}
