package io.github.wphillipmoore.mq.rest.admin.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MqRestAuthExceptionTest {

  @Test
  void constructWithoutCause() {
    MqRestAuthException ex = new MqRestAuthException("fail", "https://host/api", 401);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getUrl()).isEqualTo("https://host/api");
    assertThat(ex.getStatusCode()).isEqualTo(401);
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void constructWithCause() {
    Throwable cause = new RuntimeException("root");
    MqRestAuthException ex = new MqRestAuthException("fail", "https://host/api", 403, cause);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getUrl()).isEqualTo("https://host/api");
    assertThat(ex.getStatusCode()).isEqualTo(403);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void nullUrlThrowsWithoutCause() {
    assertThatThrownBy(() -> new MqRestAuthException("fail", null, 401))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("url");
  }

  @Test
  void nullUrlThrowsWithCause() {
    Throwable cause = new RuntimeException("root");
    assertThatThrownBy(() -> new MqRestAuthException("fail", null, 401, cause))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("url");
  }

  @Test
  void nullStatusCodeAcceptedWithoutCause() {
    MqRestAuthException ex = new MqRestAuthException("fail", "https://host/api", null);
    assertThat(ex.getStatusCode()).isNull();
  }

  @Test
  void nullStatusCodeAcceptedWithCause() {
    Throwable cause = new RuntimeException("root");
    MqRestAuthException ex = new MqRestAuthException("fail", "https://host/api", null, cause);
    assertThat(ex.getStatusCode()).isNull();
  }

  @Test
  void isMqRestException() {
    MqRestAuthException ex = new MqRestAuthException("fail", "https://host/api", 401);
    assertThat(ex).isInstanceOf(MqRestException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
