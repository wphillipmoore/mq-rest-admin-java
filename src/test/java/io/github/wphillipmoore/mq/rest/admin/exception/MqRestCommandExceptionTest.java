package io.github.wphillipmoore.mq.rest.admin.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MqRestCommandExceptionTest {

  @Test
  void constructWithoutCause() {
    Map<String, Object> payload = Map.of("reason", "2035");
    MqRestCommandException ex = new MqRestCommandException("fail", payload, 400);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getPayload()).isEqualTo(Map.of("reason", "2035"));
    assertThat(ex.getStatusCode()).isEqualTo(400);
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void constructWithCause() {
    Throwable cause = new RuntimeException("root");
    Map<String, Object> payload = Map.of("reason", "2035");
    MqRestCommandException ex = new MqRestCommandException("fail", payload, 400, cause);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getPayload()).isEqualTo(Map.of("reason", "2035"));
    assertThat(ex.getStatusCode()).isEqualTo(400);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void nullPayloadThrowsWithoutCause() {
    assertThatThrownBy(() -> new MqRestCommandException("fail", null, 400))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("payload");
  }

  @Test
  void nullPayloadThrowsWithCause() {
    Throwable cause = new RuntimeException("root");
    assertThatThrownBy(() -> new MqRestCommandException("fail", null, 400, cause))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("payload");
  }

  @Test
  void nullStatusCodeAcceptedWithoutCause() {
    MqRestCommandException ex = new MqRestCommandException("fail", Map.of(), null);
    assertThat(ex.getStatusCode()).isNull();
  }

  @Test
  void nullStatusCodeAcceptedWithCause() {
    Throwable cause = new RuntimeException("root");
    MqRestCommandException ex = new MqRestCommandException("fail", Map.of(), null, cause);
    assertThat(ex.getStatusCode()).isNull();
  }

  @Test
  void payloadIsDefensivelyCopied() {
    Map<String, Object> original = new HashMap<>();
    original.put("key", "value");
    MqRestCommandException ex = new MqRestCommandException("fail", original, 400);
    original.put("extra", "sneaky");
    assertThat(ex.getPayload()).doesNotContainKey("extra");
  }

  @Test
  void payloadIsUnmodifiable() {
    MqRestCommandException ex = new MqRestCommandException("fail", Map.of("k", "v"), 400);
    assertThatThrownBy(() -> ex.getPayload().put("new", "entry"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void isMqRestException() {
    MqRestCommandException ex = new MqRestCommandException("fail", Map.of(), 400);
    assertThat(ex).isInstanceOf(MqRestException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
