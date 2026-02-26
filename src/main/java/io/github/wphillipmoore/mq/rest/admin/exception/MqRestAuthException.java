package io.github.wphillipmoore.mq.rest.admin.exception;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when authentication or authorization fails against the MQ REST API.
 *
 * <p>The {@code statusCode} may be {@code null} if the HTTP status code was not available (e.g.,
 * connection refused before a response was received).
 */
public final class MqRestAuthException extends MqRestException {

  private static final long serialVersionUID = 1L;

  private final String url;
  private final @Nullable Integer statusCode;

  /**
   * Creates an auth exception.
   *
   * @param message description of the failure
   * @param url the URL that was being accessed
   * @param statusCode the HTTP status code, or {@code null} if unavailable
   */
  public MqRestAuthException(String message, String url, @Nullable Integer statusCode) {
    super(message);
    this.url = Objects.requireNonNull(url, "url");
    this.statusCode = statusCode;
  }

  /**
   * Creates an auth exception with a cause.
   *
   * @param message description of the failure
   * @param url the URL that was being accessed
   * @param statusCode the HTTP status code, or {@code null} if unavailable
   * @param cause the underlying cause
   */
  public MqRestAuthException(String message, String url, Integer statusCode, Throwable cause) {
    super(message, cause);
    this.url = Objects.requireNonNull(url, "url");
    this.statusCode = statusCode;
  }

  /** Returns the URL that was being accessed when the auth failure occurred. */
  public String getUrl() {
    return url;
  }

  /**
   * Returns the HTTP status code, or {@code null} if the status code was not available.
   *
   * @return the status code, or {@code null}
   */
  public @Nullable Integer getStatusCode() {
    return statusCode;
  }
}
