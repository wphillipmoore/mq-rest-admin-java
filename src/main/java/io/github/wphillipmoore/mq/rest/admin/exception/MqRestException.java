package io.github.wphillipmoore.mq.rest.admin.exception;

/**
 * Base exception for all MQ REST API errors.
 *
 * <p>This is an unchecked exception hierarchy. All MQ REST errors extend this sealed class.
 */
public sealed class MqRestException extends RuntimeException
    permits MqRestTransportException,
        MqRestResponseException,
        MqRestAuthException,
        MqRestCommandException,
        MqRestTimeoutException {

  /** Creates an exception with the given message. */
  public MqRestException(String message) {
    super(message);
  }

  /** Creates an exception with the given message and cause. */
  public MqRestException(String message, Throwable cause) {
    super(message, cause);
  }
}
