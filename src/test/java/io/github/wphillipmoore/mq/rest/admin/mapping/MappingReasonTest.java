package io.github.wphillipmoore.mq.rest.admin.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MappingReasonTest {

  @Test
  void hasExactlyThreeValues() {
    assertThat(MappingReason.values())
        .containsExactly(
            MappingReason.UNKNOWN_KEY,
            MappingReason.UNKNOWN_VALUE,
            MappingReason.UNKNOWN_QUALIFIER);
  }

  @Test
  void valueOfRoundTripsUnknownKey() {
    assertThat(MappingReason.valueOf("UNKNOWN_KEY")).isEqualTo(MappingReason.UNKNOWN_KEY);
  }

  @Test
  void valueOfRoundTripsUnknownValue() {
    assertThat(MappingReason.valueOf("UNKNOWN_VALUE")).isEqualTo(MappingReason.UNKNOWN_VALUE);
  }

  @Test
  void valueOfRoundTripsUnknownQualifier() {
    assertThat(MappingReason.valueOf("UNKNOWN_QUALIFIER"))
        .isEqualTo(MappingReason.UNKNOWN_QUALIFIER);
  }
}
