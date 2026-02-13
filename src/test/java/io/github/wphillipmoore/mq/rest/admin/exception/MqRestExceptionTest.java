package io.github.wphillipmoore.mq.rest.admin.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MqRestExceptionTest {

  @Test
  void constructWithMessage() {
    MqRestException ex = new MqRestTransportException("fail", "https://host/api");
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void constructWithMessageAndCause() {
    Throwable cause = new RuntimeException("root");
    MqRestException ex = new MqRestTransportException("fail", "https://host/api", cause);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void isRuntimeException() {
    MqRestException ex = new MqRestTransportException("fail", "https://host/api");
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
