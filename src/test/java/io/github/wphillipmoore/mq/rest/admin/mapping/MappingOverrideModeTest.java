package io.github.wphillipmoore.mq.rest.admin.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MappingOverrideModeTest {

  @Test
  void enumHasExactlyTwoValues() {
    assertThat(MappingOverrideMode.values()).hasSize(2);
  }

  @Test
  void mergeValueOfRoundTrips() {
    assertThat(MappingOverrideMode.valueOf("MERGE")).isEqualTo(MappingOverrideMode.MERGE);
  }

  @Test
  void replaceValueOfRoundTrips() {
    assertThat(MappingOverrideMode.valueOf("REPLACE")).isEqualTo(MappingOverrideMode.REPLACE);
  }
}
