package io.github.wphillipmoore.mq.rest.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BasicAuthTest {

  @Test
  void constructionWithValidValues() {
    BasicAuth auth = new BasicAuth("admin", "secret");

    assertThat(auth.username()).isEqualTo("admin");
    assertThat(auth.password()).isEqualTo("secret");
  }

  @Test
  void nullUsernameThrowsNullPointerException() {
    assertThatThrownBy(() -> new BasicAuth(null, "secret"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("username");
  }

  @Test
  void nullPasswordThrowsNullPointerException() {
    assertThatThrownBy(() -> new BasicAuth("admin", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("password");
  }

  @Test
  void equalityForSameValues() {
    BasicAuth first = new BasicAuth("admin", "secret");
    BasicAuth second = new BasicAuth("admin", "secret");

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void toStringContainsFieldValues() {
    BasicAuth auth = new BasicAuth("admin", "secret");

    assertThat(auth.toString()).contains("admin");
    assertThat(auth.toString()).contains("secret");
  }
}
