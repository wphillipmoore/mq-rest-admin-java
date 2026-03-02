package io.github.wphillipmoore.mq.rest.admin.exception;

import java.util.Objects;

/** Thrown when a network or connection failure occurs communicating with the MQ REST API. */
public final class MqRestTransportException extends MqRestException {

  private static final long serialVersionUID = 1L;

  private final String url;

  /**
   * Creates a transport exception.
   *
   * @param message description of the failure
   * @param url the URL that was being accessed
   */
  public MqRestTransportException(String message, String url) {
    super(message);
    this.url = Objects.requireNonNull(url, "url");
  }

  /**
   * Creates a transport exception with a cause.
   *
   * @param message description of the failure
   * @param url the URL that was being accessed
   * @param cause the underlying cause
   */
  public MqRestTransportException(String message, String url, Throwable cause) {
    super(message, cause);
    this.url = Objects.requireNonNull(url, "url");
  }

  /** Returns the URL that was being accessed when the failure occurred. */
  public String getUrl() {
    return url;
  }
}
