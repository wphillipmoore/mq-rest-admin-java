package io.github.wphillipmoore.mq.rest.admin.exception;

/**
 * Thrown when the MQ REST API returns a malformed or unexpected response.
 *
 * <p>The {@code responseText} may be {@code null} if the response body was not available.
 */
public final class MqRestResponseException extends MqRestException {

  private final String responseText;

  /**
   * Creates a response exception.
   *
   * @param message description of the failure
   * @param responseText the raw response text, or {@code null} if unavailable
   */
  public MqRestResponseException(String message, String responseText) {
    super(message);
    this.responseText = responseText;
  }

  /**
   * Creates a response exception with a cause.
   *
   * @param message description of the failure
   * @param responseText the raw response text, or {@code null} if unavailable
   * @param cause the underlying cause
   */
  public MqRestResponseException(String message, String responseText, Throwable cause) {
    super(message, cause);
    this.responseText = responseText;
  }

  /**
   * Returns the raw response text, or {@code null} if the response body was not available.
   *
   * @return the response text, or {@code null}
   */
  public String getResponseText() {
    return responseText;
  }
}
