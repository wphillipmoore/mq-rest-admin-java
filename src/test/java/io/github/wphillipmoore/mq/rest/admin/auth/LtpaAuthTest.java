package io.github.wphillipmoore.mq.rest.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LtpaAuthTest {

  @Test
  void constructionWithValidValues() {
    LtpaAuth auth = new LtpaAuth("admin", "secret");

    assertThat(auth.username()).isEqualTo("admin");
    assertThat(auth.password()).isEqualTo("secret");
  }

  @Test
  void nullUsernameThrowsNullPointerException() {
    assertThatThrownBy(() -> new LtpaAuth(null, "secret"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("username");
  }

  @Test
  void nullPasswordThrowsNullPointerException() {
    assertThatThrownBy(() -> new LtpaAuth("admin", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("password");
  }

  @Test
  void equalityForSameValues() {
    LtpaAuth first = new LtpaAuth("admin", "secret");
    LtpaAuth second = new LtpaAuth("admin", "secret");

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void toStringContainsFieldValues() {
    LtpaAuth auth = new LtpaAuth("admin", "secret");

    assertThat(auth.toString()).contains("admin");
    assertThat(auth.toString()).contains("secret");
  }
}
