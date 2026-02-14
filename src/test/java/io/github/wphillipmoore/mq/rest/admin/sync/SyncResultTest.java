package io.github.wphillipmoore.mq.rest.admin.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SyncResultTest {

  @Test
  void constructorWithValidValues() {
    SyncResult result = new SyncResult(SyncOperation.STARTED, 3, 2.5);
    assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
    assertThat(result.polls()).isEqualTo(3);
    assertThat(result.elapsedSeconds()).isEqualTo(2.5);
  }

  @Test
  void nullOperationThrowsNullPointerException() {
    assertThatThrownBy(() -> new SyncResult(null, 1, 1.0))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("operation");
  }

  @Test
  void negativePollsThrows() {
    assertThatThrownBy(() -> new SyncResult(SyncOperation.STOPPED, -1, 1.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("polls must be >= 0");
  }

  @Test
  void negativeElapsedThrows() {
    assertThatThrownBy(() -> new SyncResult(SyncOperation.STOPPED, 1, -0.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elapsedSeconds must be >= 0");
  }

  @Test
  void zeroPollsAndZeroElapsedAccepted() {
    SyncResult result = new SyncResult(SyncOperation.RESTARTED, 0, 0.0);
    assertThat(result.polls()).isZero();
    assertThat(result.elapsedSeconds()).isZero();
  }

  @Test
  void equalityAndToString() {
    SyncResult a = new SyncResult(SyncOperation.STARTED, 5, 3.0);
    SyncResult b = new SyncResult(SyncOperation.STARTED, 5, 3.0);
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).contains("STARTED").contains("5").contains("3.0");
  }
}
