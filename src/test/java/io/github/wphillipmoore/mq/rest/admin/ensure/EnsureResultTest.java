package io.github.wphillipmoore.mq.rest.admin.ensure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnsureResultTest {

  @Test
  void constructsWithActionAndChangedList() {
    EnsureResult result = new EnsureResult(EnsureAction.UPDATED, List.of("attr1", "attr2"));

    assertThat(result.action()).isEqualTo(EnsureAction.UPDATED);
    assertThat(result.changed()).containsExactly("attr1", "attr2");
  }

  @Test
  void nullActionThrowsNpe() {
    assertThatThrownBy(() -> new EnsureResult(null, List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("action");
  }

  @Test
  void nullChangedDefaultsToEmptyList() {
    EnsureResult result = new EnsureResult(EnsureAction.CREATED, null);

    assertThat(result.changed()).isEmpty();
  }

  @Test
  void changedListIsUnmodifiable() {
    List<String> mutable = new ArrayList<>(List.of("attr1"));
    EnsureResult result = new EnsureResult(EnsureAction.UPDATED, mutable);

    assertThatThrownBy(() -> result.changed().add("attr2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void changedListIsDefensiveCopy() {
    List<String> mutable = new ArrayList<>(List.of("attr1"));
    EnsureResult result = new EnsureResult(EnsureAction.UPDATED, mutable);

    mutable.add("attr2");

    assertThat(result.changed()).containsExactly("attr1");
  }

  @Test
  void equalityWorks() {
    EnsureResult a = new EnsureResult(EnsureAction.CREATED, List.of());
    EnsureResult b = new EnsureResult(EnsureAction.CREATED, null);

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void toStringContainsFields() {
    EnsureResult result = new EnsureResult(EnsureAction.UPDATED, List.of("key1"));

    assertThat(result.toString()).contains("UPDATED").contains("key1");
  }

  @Test
  void inequalityOnAction() {
    EnsureResult a = new EnsureResult(EnsureAction.CREATED, List.of());
    EnsureResult b = new EnsureResult(EnsureAction.UNCHANGED, List.of());

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void inequalityOnChanged() {
    EnsureResult a = new EnsureResult(EnsureAction.UPDATED, List.of("x"));
    EnsureResult b = new EnsureResult(EnsureAction.UPDATED, List.of("y"));

    assertThat(a).isNotEqualTo(b);
  }
}
