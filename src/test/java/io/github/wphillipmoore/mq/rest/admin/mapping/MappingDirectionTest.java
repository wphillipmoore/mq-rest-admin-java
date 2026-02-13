package io.github.wphillipmoore.mq.rest.admin.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MappingDirectionTest {

  @Test
  void hasExactlyTwoValues() {
    assertThat(MappingDirection.values())
        .containsExactly(MappingDirection.REQUEST, MappingDirection.RESPONSE);
  }

  @Test
  void valueOfRoundTripsRequest() {
    assertThat(MappingDirection.valueOf("REQUEST")).isEqualTo(MappingDirection.REQUEST);
  }

  @Test
  void valueOfRoundTripsResponse() {
    assertThat(MappingDirection.valueOf("RESPONSE")).isEqualTo(MappingDirection.RESPONSE);
  }
}
