package io.github.wphillipmoore.mq.rest.admin;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable response from an MQ REST API transport operation.
 *
 * <p>Mirrors pymqrest's {@code TransportResponse} frozen dataclass. Headers are defensively copied
 * to guarantee unmodifiability.
 *
 * @param statusCode the HTTP status code
 * @param body the response body text, never null (empty string if no body)
 * @param headers the response headers, never null, unmodifiable
 */
public record TransportResponse(int statusCode, String body, Map<String, String> headers) {

  /** Validates non-null fields and defensively copies headers. */
  public TransportResponse {
    Objects.requireNonNull(body, "body");
    headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
  }
}
