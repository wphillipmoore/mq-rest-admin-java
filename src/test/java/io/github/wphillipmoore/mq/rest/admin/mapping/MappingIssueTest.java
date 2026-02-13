package io.github.wphillipmoore.mq.rest.admin.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MappingIssueTest {

  @Test
  void constructionWithAllFields() {
    MappingIssue issue =
        new MappingIssue(
            MappingDirection.REQUEST, MappingReason.UNKNOWN_VALUE, "status", "XYZ", 0, "queue");

    assertThat(issue.direction()).isEqualTo(MappingDirection.REQUEST);
    assertThat(issue.reason()).isEqualTo(MappingReason.UNKNOWN_VALUE);
    assertThat(issue.attributeName()).isEqualTo("status");
    assertThat(issue.attributeValue()).isEqualTo("XYZ");
    assertThat(issue.objectIndex()).isEqualTo(0);
    assertThat(issue.qualifier()).isEqualTo("queue");
  }

  @Test
  void constructionWithNullableFieldsAsNull() {
    MappingIssue issue =
        new MappingIssue(
            MappingDirection.RESPONSE, MappingReason.UNKNOWN_KEY, "badAttr", null, null, null);

    assertThat(issue.attributeValue()).isNull();
    assertThat(issue.objectIndex()).isNull();
    assertThat(issue.qualifier()).isNull();
  }

  @Test
  void nullDirectionThrowsNullPointerException() {
    assertThatThrownBy(
            () -> new MappingIssue(null, MappingReason.UNKNOWN_KEY, "attr", null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("direction");
  }

  @Test
  void nullReasonThrowsNullPointerException() {
    assertThatThrownBy(
            () -> new MappingIssue(MappingDirection.REQUEST, null, "attr", null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("reason");
  }

  @Test
  void nullAttributeNameThrowsNullPointerException() {
    assertThatThrownBy(
            () ->
                new MappingIssue(
                    MappingDirection.REQUEST, MappingReason.UNKNOWN_KEY, null, null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("attributeName");
  }

  @Test
  void equalityForSameValues() {
    MappingIssue first =
        new MappingIssue(
            MappingDirection.REQUEST, MappingReason.UNKNOWN_KEY, "attr", "val", 1, "queue");
    MappingIssue second =
        new MappingIssue(
            MappingDirection.REQUEST, MappingReason.UNKNOWN_KEY, "attr", "val", 1, "queue");

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void toStringContainsFieldValues() {
    MappingIssue issue =
        new MappingIssue(
            MappingDirection.REQUEST, MappingReason.UNKNOWN_KEY, "badAttr", null, null, "queue");

    assertThat(issue.toString()).contains("REQUEST");
    assertThat(issue.toString()).contains("UNKNOWN_KEY");
    assertThat(issue.toString()).contains("badAttr");
    assertThat(issue.toString()).contains("queue");
  }
}
