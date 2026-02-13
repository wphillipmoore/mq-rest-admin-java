package io.github.wphillipmoore.mq.rest.admin.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingDataTest {

  private static final String VALID_JSON =
      """
      {
        "commands": {
          "DISPLAY QUEUE": {"qualifier": "queue"}
        },
        "qualifiers": {
          "queue": {
            "request_key_map": {"max_depth": "MAXDEPTH"},
            "response_key_map": {"MAXDEPTH": "max_depth"}
          }
        }
      }
      """;

  @Test
  void loadDefaultReturnsNonNull() {
    MappingData data = MappingData.loadDefault();

    assertThat(data).isNotNull();
  }

  @Test
  void loadDefaultContainsExpectedCommand() {
    MappingData data = MappingData.loadDefault();

    assertThat(data.getQualifierForCommand("DISPLAY QUEUE")).isEqualTo("queue");
  }

  @Test
  void fromJsonParsesValidJson() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    assertThat(data.getQualifierForCommand("DISPLAY QUEUE")).isEqualTo("queue");
  }

  @Test
  void fromMapCreatesFromProgrammaticMap() {
    Map<String, Object> commands = new LinkedHashMap<>();
    commands.put("ALTER QUEUE", Map.of("qualifier", "queue"));
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("commands", commands);
    map.put("qualifiers", Map.of());

    MappingData data = MappingData.fromMap(map);

    assertThat(data.getQualifierForCommand("ALTER QUEUE")).isEqualTo("queue");
  }

  @Test
  void getQualifierForCommandReturnsNullForUnknownCommand() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    assertThat(data.getQualifierForCommand("UNKNOWN COMMAND")).isNull();
  }

  @Test
  void hasQualifierReturnsTrueForKnownQualifier() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    assertThat(data.hasQualifier("queue")).isTrue();
  }

  @Test
  void hasQualifierReturnsFalseForUnknownQualifier() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    assertThat(data.hasQualifier("channel")).isFalse();
  }

  @Test
  void getQualifierDataReturnsDataForKnownQualifier() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    Map<String, Object> qualifierData = data.getQualifierData("queue");

    assertThat(qualifierData).isNotNull();
    assertThat(qualifierData).containsKey("request_key_map");
  }

  @Test
  void getQualifierDataReturnsNullForUnknownQualifier() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    assertThat(data.getQualifierData("channel")).isNull();
  }

  @Test
  void withOverridesMergeMergesNewEntries() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of("DISPLAY CHANNEL", Map.of("qualifier", "channel")));

    MappingData merged = base.withOverrides(overrides, MappingOverrideMode.MERGE);

    assertThat(merged.getQualifierForCommand("DISPLAY QUEUE")).isEqualTo("queue");
    assertThat(merged.getQualifierForCommand("DISPLAY CHANNEL")).isEqualTo("channel");
  }

  @Test
  void withOverridesMergeOverridesExistingEntries() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of("DISPLAY QUEUE", Map.of("qualifier", "local_queue")));

    MappingData merged = base.withOverrides(overrides, MappingOverrideMode.MERGE);

    assertThat(merged.getQualifierForCommand("DISPLAY QUEUE")).isEqualTo("local_queue");
  }

  @Test
  void withOverridesReplaceReplacesDataEntirely() {
    String replaceJson =
        """
        {
          "commands": {
            "DISPLAY QUEUE": {"qualifier": "queue"},
            "ALTER QUEUE": {"qualifier": "queue"}
          },
          "qualifiers": {
            "queue": {
              "request_key_map": {"depth": "CURDEPTH"},
              "response_key_map": {"CURDEPTH": "depth"}
            }
          }
        }
        """;
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put(
        "commands",
        Map.of(
            "DISPLAY QUEUE",
            Map.of("qualifier", "queue"),
            "ALTER QUEUE",
            Map.of("qualifier", "queue")));
    Map<String, Object> qualifierData = new LinkedHashMap<>();
    qualifierData.put("queue", Map.of("request_key_map", Map.of("depth", "CURDEPTH")));
    overrides.put("qualifiers", qualifierData);

    MappingData replaced = base.withOverrides(overrides, MappingOverrideMode.REPLACE);

    assertThat(replaced.getQualifierForCommand("DISPLAY QUEUE")).isEqualTo("queue");
    assertThat(replaced.getQualifierForCommand("ALTER QUEUE")).isEqualTo("queue");
  }

  @Test
  void withOverridesReplaceThrowsForIncompletCommands() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of());
    overrides.put("qualifiers", Map.of("queue", Map.of()));

    assertThatThrownBy(() -> base.withOverrides(overrides, MappingOverrideMode.REPLACE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command");
  }

  @Test
  void withOverridesReplaceThrowsForIncompleteQualifiers() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of("DISPLAY QUEUE", Map.of("qualifier", "queue")));
    overrides.put("qualifiers", Map.of());

    assertThatThrownBy(() -> base.withOverrides(overrides, MappingOverrideMode.REPLACE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("qualifier");
  }

  @Test
  void withOverridesInvalidTopLevelKeyThrowsIllegalArgumentException() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = Map.of("invalid_key", Map.of());

    assertThatThrownBy(() -> base.withOverrides(overrides, MappingOverrideMode.MERGE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid_key");
  }

  @Test
  void fromJsonNullThrowsNullPointerException() {
    assertThatThrownBy(() -> MappingData.fromJson(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("json");
  }

  @Test
  void fromJsonEmptyStringThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> MappingData.fromJson(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void fromMapNullThrowsNullPointerException() {
    assertThatThrownBy(() -> MappingData.fromMap(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("data");
  }

  @Test
  void withOverridesNullOverridesThrowsNullPointerException() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    assertThatThrownBy(() -> data.withOverrides(null, MappingOverrideMode.MERGE))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("overrides");
  }

  @Test
  void withOverridesNullModeThrowsNullPointerException() {
    MappingData data = MappingData.fromJson(VALID_JSON);

    assertThatThrownBy(() -> data.withOverrides(Map.of(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("mode");
  }

  @Test
  void fromMapDoesNotMutateOriginal() {
    Map<String, Object> commands = new LinkedHashMap<>();
    commands.put("CMD", Map.of("qualifier", "q"));
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("commands", commands);
    map.put("qualifiers", Map.of());

    MappingData data = MappingData.fromMap(map);
    commands.put("NEW_CMD", Map.of("qualifier", "q2"));

    assertThat(data.getQualifierForCommand("NEW_CMD")).isNull();
  }

  @Test
  void getQualifierForCommandReturnsNullWhenNoCommandsSection() {
    MappingData data = MappingData.fromMap(Map.of());

    assertThat(data.getQualifierForCommand("DISPLAY QUEUE")).isNull();
  }

  @Test
  void hasQualifierReturnsFalseWhenNoQualifiersSection() {
    MappingData data = MappingData.fromMap(Map.of());

    assertThat(data.hasQualifier("queue")).isFalse();
  }

  @Test
  void loadFromResourceThrowsForMissingResource() {
    assertThatThrownBy(() -> MappingData.loadFromResource("nonexistent.json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nonexistent.json");
  }

  @Test
  void fromJsonNullLiteralThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> MappingData.fromJson("null"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void withOverridesMergeWhenBaseSectionMissing() {
    MappingData base = MappingData.fromMap(Map.of());
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of("DISPLAY QUEUE", Map.of("qualifier", "queue")));

    MappingData merged = base.withOverrides(overrides, MappingOverrideMode.MERGE);

    assertThat(merged.getQualifierForCommand("DISPLAY QUEUE")).isEqualTo("queue");
  }

  @Test
  void withOverridesMergeWithScalarOverrideValue() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    Map<String, Object> commandOverrides = new LinkedHashMap<>();
    commandOverrides.put("DISPLAY QUEUE", "scalar_value");
    overrides.put("commands", commandOverrides);

    MappingData merged = base.withOverrides(overrides, MappingOverrideMode.MERGE);

    // The scalar replaces the Map entry
    assertThat(merged.getQualifierForCommand("DISPLAY QUEUE")).isNull();
  }

  @Test
  void withOverridesReplaceWhenOverrideCommandsNotMap() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", "not_a_map");
    overrides.put("qualifiers", Map.of("queue", Map.of()));

    assertThatThrownBy(() -> base.withOverrides(overrides, MappingOverrideMode.REPLACE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command");
  }

  @Test
  void withOverridesReplaceWhenOverrideQualifiersNotMap() {
    MappingData base = MappingData.fromJson(VALID_JSON);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of("DISPLAY QUEUE", Map.of("qualifier", "queue")));
    overrides.put("qualifiers", "not_a_map");

    assertThatThrownBy(() -> base.withOverrides(overrides, MappingOverrideMode.REPLACE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("qualifier");
  }

  @Test
  void getQualifierForCommandReturnsNullWhenCommandEntryNotMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("commands", Map.of("BAD_CMD", "not_a_map"));
    MappingData data = MappingData.fromMap(map);

    assertThat(data.getQualifierForCommand("BAD_CMD")).isNull();
  }

  @Test
  void getQualifierForCommandReturnsNullWhenQualifierFieldNotString() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("commands", Map.of("CMD", Map.of("qualifier", 123)));
    MappingData data = MappingData.fromMap(map);

    assertThat(data.getQualifierForCommand("CMD")).isNull();
  }

  @Test
  void withOverridesReplaceSucceedsWhenBaseHasNoCommands() {
    MappingData base = MappingData.fromMap(Map.of());
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of("NEW", Map.of("qualifier", "q")));

    MappingData replaced = base.withOverrides(overrides, MappingOverrideMode.REPLACE);

    assertThat(replaced.getQualifierForCommand("NEW")).isEqualTo("q");
  }

  @Test
  void withOverridesReplaceSucceedsWhenBaseHasEmptyCommandsAndQualifiers() {
    Map<String, Object> baseMap = new LinkedHashMap<>();
    baseMap.put("commands", new LinkedHashMap<>());
    baseMap.put("qualifiers", new LinkedHashMap<>());
    MappingData base = MappingData.fromMap(baseMap);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("commands", Map.of("NEW", Map.of("qualifier", "q")));
    overrides.put("qualifiers", Map.of("q", Map.of()));

    MappingData replaced = base.withOverrides(overrides, MappingOverrideMode.REPLACE);

    assertThat(replaced.getQualifierForCommand("NEW")).isEqualTo("q");
  }
}
