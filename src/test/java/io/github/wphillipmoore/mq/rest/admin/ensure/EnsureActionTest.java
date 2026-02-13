package io.github.wphillipmoore.mq.rest.admin.ensure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnsureActionTest {

  @Test
  void hasExactlyThreeValues() {
    assertThat(EnsureAction.values()).hasSize(3);
  }

  @Test
  void valuesInExpectedOrder() {
    assertThat(EnsureAction.values())
        .containsExactly(EnsureAction.CREATED, EnsureAction.UPDATED, EnsureAction.UNCHANGED);
  }

  @Test
  void valueOfRoundTrips() {
    for (EnsureAction action : EnsureAction.values()) {
      assertThat(EnsureAction.valueOf(action.name())).isEqualTo(action);
    }
  }
}
