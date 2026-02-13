package io.github.wphillipmoore.mq.rest.admin.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MqRestTimeoutExceptionTest {

  @Test
  void constructWithoutCause() {
    MqRestTimeoutException ex = new MqRestTimeoutException("fail", "Q1", "display", 5.5);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getName()).isEqualTo("Q1");
    assertThat(ex.getOperation()).isEqualTo("display");
    assertThat(ex.getElapsed()).isEqualTo(5.5);
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void constructWithCause() {
    Throwable cause = new RuntimeException("root");
    MqRestTimeoutException ex = new MqRestTimeoutException("fail", "Q1", "display", 5.5, cause);
    assertThat(ex.getMessage()).isEqualTo("fail");
    assertThat(ex.getName()).isEqualTo("Q1");
    assertThat(ex.getOperation()).isEqualTo("display");
    assertThat(ex.getElapsed()).isEqualTo(5.5);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void nullNameThrowsWithoutCause() {
    assertThatThrownBy(() -> new MqRestTimeoutException("fail", null, "display", 5.5))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name");
  }

  @Test
  void nullNameThrowsWithCause() {
    Throwable cause = new RuntimeException("root");
    assertThatThrownBy(() -> new MqRestTimeoutException("fail", null, "display", 5.5, cause))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name");
  }

  @Test
  void nullOperationThrowsWithoutCause() {
    assertThatThrownBy(() -> new MqRestTimeoutException("fail", "Q1", null, 5.5))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("operation");
  }

  @Test
  void nullOperationThrowsWithCause() {
    Throwable cause = new RuntimeException("root");
    assertThatThrownBy(() -> new MqRestTimeoutException("fail", "Q1", null, 5.5, cause))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("operation");
  }

  @Test
  void isMqRestException() {
    MqRestTimeoutException ex = new MqRestTimeoutException("fail", "Q1", "display", 0.0);
    assertThat(ex).isInstanceOf(MqRestException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
