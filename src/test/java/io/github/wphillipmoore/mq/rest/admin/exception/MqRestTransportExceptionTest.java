package io.github.wphillipmoore.mq.rest.admin.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MqRestTransportExceptionTest {

  @Test
  void constructWithoutCause() {
    MqRestTransportException ex = new MqRestTransportException("fail", "https://host/api");
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getUrl()).isEqualTo("https://host/api");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void constructWithCause() {
    Throwable cause = new RuntimeException("root");
    MqRestTransportException ex = new MqRestTransportException("fail", "https://host/api", cause);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getUrl()).isEqualTo("https://host/api");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void nullUrlThrowsWithoutCause() {
    assertThatThrownBy(() -> new MqRestTransportException("fail", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("url");
  }

  @Test
  void nullUrlThrowsWithCause() {
    Throwable cause = new RuntimeException("root");
    assertThatThrownBy(() -> new MqRestTransportException("fail", null, cause))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("url");
  }

  @Test
  void isMqRestException() {
    MqRestTransportException ex = new MqRestTransportException("fail", "https://host/api");
    assertThat(ex).isInstanceOf(MqRestException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
