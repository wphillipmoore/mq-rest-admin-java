package io.github.wphillipmoore.mq.rest.admin.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Thrown when an MQSC command returns an error response from the MQ REST API.
 *
 * <p>The {@code payload} is an unmodifiable copy of the error response. The {@code statusCode} may
 * be {@code null} if the HTTP status code was not available.
 */
public final class MqRestCommandException extends MqRestException {

  private final Map<String, Object> payload;
  private final Integer statusCode;

  /**
   * Creates a command exception.
   *
   * @param message description of the failure
   * @param payload the error response payload (defensively copied as unmodifiable)
   * @param statusCode the HTTP status code, or {@code null} if unavailable
   */
  public MqRestCommandException(String message, Map<String, Object> payload, Integer statusCode) {
    super(message);
    this.payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    this.statusCode = statusCode;
  }

  /**
   * Creates a command exception with a cause.
   *
   * @param message description of the failure
   * @param payload the error response payload (defensively copied as unmodifiable)
   * @param statusCode the HTTP status code, or {@code null} if unavailable
   * @param cause the underlying cause
   */
  public MqRestCommandException(
      String message, Map<String, Object> payload, Integer statusCode, Throwable cause) {
    super(message, cause);
    this.payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    this.statusCode = statusCode;
  }

  /**
   * Returns the error response payload. The returned map is unmodifiable.
   *
   * @return an unmodifiable map of the error response
   */
  public Map<String, Object> getPayload() {
    return payload;
  }

  /**
   * Returns the HTTP status code, or {@code null} if the status code was not available.
   *
   * @return the status code, or {@code null}
   */
  public Integer getStatusCode() {
    return statusCode;
  }
}
