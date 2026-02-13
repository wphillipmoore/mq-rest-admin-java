package io.github.wphillipmoore.mq.rest.admin.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MqRestResponseExceptionTest {

  @Test
  void constructWithoutCause() {
    MqRestResponseException ex = new MqRestResponseException("fail", "{\"error\":true}");
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getResponseText()).isEqualTo("{\"error\":true}");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void constructWithCause() {
    Throwable cause = new RuntimeException("root");
    MqRestResponseException ex = new MqRestResponseException("fail", "{\"error\":true}", cause);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getResponseText()).isEqualTo("{\"error\":true}");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void nullResponseTextAcceptedWithoutCause() {
    MqRestResponseException ex = new MqRestResponseException("fail", null);
    assertThat(ex.getResponseText()).isNull();
  }

  @Test
  void nullResponseTextAcceptedWithCause() {
    Throwable cause = new RuntimeException("root");
    MqRestResponseException ex = new MqRestResponseException("fail", null, cause);
    assertThat(ex.getResponseText()).isNull();
  }

  @Test
  void isMqRestException() {
    MqRestResponseException ex = new MqRestResponseException("fail", null);
    assertThat(ex).isInstanceOf(MqRestException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
