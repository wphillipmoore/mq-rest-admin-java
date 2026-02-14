package io.github.wphillipmoore.mq.rest.admin;

import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Transport interface for MQ REST API HTTP communication.
 *
 * <p>Mirrors pymqrest's {@code MQRESTTransport} protocol. Implementations handle the actual HTTP
 * communication and should throw {@link
 * io.github.wphillipmoore.mq.rest.admin.exception.MqRestTransportException} for network or
 * connection failures.
 */
public interface MqRestTransport {

  /**
   * Sends a JSON POST request to the MQ REST API.
   *
   * @param url fully-qualified URL to send the request to
   * @param payload JSON-serializable request body
   * @param headers HTTP headers to include in the request
   * @param timeout request timeout, or {@code null} for no timeout
   * @param verifyTls whether to verify TLS certificates
   * @return the transport response
   */
  TransportResponse postJson(
      String url,
      Map<String, Object> payload,
      Map<String, String> headers,
      @Nullable Duration timeout,
      boolean verifyTls);
}
