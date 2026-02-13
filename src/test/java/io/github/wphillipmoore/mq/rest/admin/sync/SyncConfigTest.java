package io.github.wphillipmoore.mq.rest.admin.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SyncConfigTest {

  @Test
  void noArgConstructorUsesDefaults() {
    SyncConfig config = new SyncConfig();
    assertThat(config.timeoutSeconds()).isEqualTo(30.0);
    assertThat(config.pollIntervalSeconds()).isEqualTo(1.0);
  }

  @Test
  void customValuesAccepted() {
    SyncConfig config = new SyncConfig(60.0, 0.5);
    assertThat(config.timeoutSeconds()).isEqualTo(60.0);
    assertThat(config.pollIntervalSeconds()).isEqualTo(0.5);
  }

  @Test
  void zeroTimeoutThrows() {
    assertThatThrownBy(() -> new SyncConfig(0, 1.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("timeoutSeconds must be > 0");
  }

  @Test
  void negativeTimeoutThrows() {
    assertThatThrownBy(() -> new SyncConfig(-1.0, 1.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("timeoutSeconds must be > 0");
  }

  @Test
  void zeroPollIntervalThrows() {
    assertThatThrownBy(() -> new SyncConfig(30.0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("pollIntervalSeconds must be > 0");
  }

  @Test
  void negativePollIntervalThrows() {
    assertThatThrownBy(() -> new SyncConfig(30.0, -0.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("pollIntervalSeconds must be > 0");
  }

  @Test
  void equalityAndToString() {
    SyncConfig a = new SyncConfig(10.0, 2.0);
    SyncConfig b = new SyncConfig(10.0, 2.0);
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).contains("10.0").contains("2.0");
  }
}
