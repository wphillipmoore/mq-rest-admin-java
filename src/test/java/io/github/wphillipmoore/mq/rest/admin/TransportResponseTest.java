package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransportResponseTest {

  @Test
  void constructionWithValidValues() {
    TransportResponse response =
        new TransportResponse(200, "{}", Map.of("Content-Type", "application/json"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{}");
    assertThat(response.headers()).containsEntry("Content-Type", "application/json");
  }

  @Test
  void nullBodyThrowsNullPointerException() {
    assertThatThrownBy(() -> new TransportResponse(200, null, Map.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("body");
  }

  @Test
  void nullHeadersThrowsNullPointerException() {
    assertThatThrownBy(() -> new TransportResponse(200, "", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("headers");
  }

  @Test
  void headersAreDefensivelyCopied() {
    HashMap<String, String> mutableHeaders = new HashMap<>();
    mutableHeaders.put("X-Key", "original");

    TransportResponse response = new TransportResponse(200, "", mutableHeaders);
    mutableHeaders.put("X-Key", "modified");

    assertThat(response.headers()).containsEntry("X-Key", "original");
  }

  @Test
  void headersAreUnmodifiable() {
    TransportResponse response = new TransportResponse(200, "", Map.of("X-Key", "value"));

    assertThatThrownBy(() -> response.headers().put("X-New", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void equalityForSameValues() {
    TransportResponse first = new TransportResponse(200, "body", Map.of("key", "value"));
    TransportResponse second = new TransportResponse(200, "body", Map.of("key", "value"));

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void toStringContainsFieldValues() {
    TransportResponse response = new TransportResponse(404, "not found", Map.of());

    assertThat(response.toString()).contains("404");
    assertThat(response.toString()).contains("not found");
  }
}
