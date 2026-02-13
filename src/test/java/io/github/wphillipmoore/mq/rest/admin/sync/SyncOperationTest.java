package io.github.wphillipmoore.mq.rest.admin.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SyncOperationTest {

  @Test
  void hasThreeValues() {
    assertThat(SyncOperation.values()).hasSize(3);
  }

  @Test
  void valueOfRoundTrips() {
    for (SyncOperation op : SyncOperation.values()) {
      assertThat(SyncOperation.valueOf(op.name())).isEqualTo(op);
    }
  }

  @Test
  void valuesOrderIsStartedStoppedRestarted() {
    SyncOperation[] values = SyncOperation.values();
    assertThat(values[0]).isEqualTo(SyncOperation.STARTED);
    assertThat(values[1]).isEqualTo(SyncOperation.STOPPED);
    assertThat(values[2]).isEqualTo(SyncOperation.RESTARTED);
  }
}
