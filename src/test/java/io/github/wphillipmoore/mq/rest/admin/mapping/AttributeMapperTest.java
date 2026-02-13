package io.github.wphillipmoore.mq.rest.admin.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttributeMapperTest {

  private static final String TEST_JSON =
      """
      {
        "commands": {
          "DISPLAY QUEUE": {"qualifier": "queue"}
        },
        "qualifiers": {
          "queue": {
            "request_key_map": {
              "max_depth": "MAXDEPTH",
              "description": "DESCR",
              "get": "GET"
            },
            "request_value_map": {
              "get": {
                "enabled": "ENABLED",
                "disabled": "DISABLED"
              }
            },
            "request_key_value_map": {
              "purge": {
                "yes": {"key": "PURGE", "value": "YES"},
                "no": {"key": "PURGE", "value": "NO"}
              }
            },
            "response_key_map": {
              "MAXDEPTH": "max_depth",
              "DESCR": "description",
              "GET": "get"
            },
            "response_value_map": {
              "GET": {
                "ENABLED": "enabled",
                "DISABLED": "disabled"
              }
            }
          }
        }
      }
      """;

  private final AttributeMapper mapper = new AttributeMapper(MappingData.fromJson(TEST_JSON));

  @Test
  void requestKeyMapping() {
    Map<String, Object> input = Map.of("max_depth", 5000);

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("MAXDEPTH", 5000);
  }

  @Test
  void requestValueMapping() {
    Map<String, Object> input = Map.of("get", "enabled");

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("GET", "ENABLED");
  }

  @Test
  void requestKeyValueMapping() {
    Map<String, Object> input = Map.of("purge", "yes");

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("PURGE", "YES");
    assertThat(result).doesNotContainKey("purge");
  }

  @Test
  void requestKeyValueUnknownValueRecordsIssue() {
    Map<String, Object> input = Map.of("purge", "maybe");

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("purge", "maybe");
  }

  @Test
  void requestKeyValueUnknownValueStrictThrows() {
    Map<String, Object> input = Map.of("purge", "maybe");

    assertThatThrownBy(() -> mapper.mapRequestAttributes("queue", input, true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(1);
              assertThat(me.getIssues().get(0).reason()).isEqualTo(MappingReason.UNKNOWN_VALUE);
              assertThat(me.getIssues().get(0).attributeName()).isEqualTo("purge");
            });
  }

  @Test
  void responseKeyMapping() {
    Map<String, Object> input = Map.of("MAXDEPTH", 5000);

    Map<String, Object> result = mapper.mapResponseAttributes("queue", input, false);

    assertThat(result).containsEntry("max_depth", 5000);
  }

  @Test
  void responseValueMapping() {
    Map<String, Object> input = Map.of("GET", "ENABLED");

    Map<String, Object> result = mapper.mapResponseAttributes("queue", input, false);

    assertThat(result).containsEntry("get", "enabled");
  }

  @Test
  void responseDoesNotUseKeyValueMap() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("purge", "yes");

    Map<String, Object> result = mapper.mapResponseAttributes("queue", input, false);

    // "purge" is not in response_key_map, so it passes through as UNKNOWN_KEY
    assertThat(result).containsEntry("purge", "yes");
    assertThat(result).doesNotContainKey("PURGE");
  }

  @Test
  void unknownKeyStrictThrows() {
    Map<String, Object> input = Map.of("unknown_attr", "val");

    assertThatThrownBy(() -> mapper.mapRequestAttributes("queue", input, true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(1);
              assertThat(me.getIssues().get(0).reason()).isEqualTo(MappingReason.UNKNOWN_KEY);
              assertThat(me.getIssues().get(0).attributeName()).isEqualTo("unknown_attr");
            });
  }

  @Test
  void unknownKeyPermissivePassesThrough() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("unknown_attr", "val");
    input.put("max_depth", 5000);

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("unknown_attr", "val");
    assertThat(result).containsEntry("MAXDEPTH", 5000);
  }

  @Test
  void unknownValueStrictThrows() {
    Map<String, Object> input = Map.of("get", "unknown_state");

    assertThatThrownBy(() -> mapper.mapRequestAttributes("queue", input, true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(1);
              assertThat(me.getIssues().get(0).reason()).isEqualTo(MappingReason.UNKNOWN_VALUE);
              assertThat(me.getIssues().get(0).attributeValue()).isEqualTo("unknown_state");
            });
  }

  @Test
  void unknownValuePermissivePassesThrough() {
    Map<String, Object> input = Map.of("get", "unknown_state");

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("GET", "unknown_state");
  }

  @Test
  void unknownQualifierStrictThrows() {
    Map<String, Object> input = Map.of("attr", "val");

    assertThatThrownBy(() -> mapper.mapRequestAttributes("nonexistent", input, true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(1);
              assertThat(me.getIssues().get(0).reason()).isEqualTo(MappingReason.UNKNOWN_QUALIFIER);
            });
  }

  @Test
  void unknownQualifierPermissiveReturnsInputUnchanged() {
    Map<String, Object> input = Map.of("attr", "val");

    Map<String, Object> result = mapper.mapRequestAttributes("nonexistent", input, false);

    assertThat(result).containsEntry("attr", "val");
  }

  @Test
  void multipleIssuesAllCollected() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("unknown1", "val1");
    input.put("unknown2", "val2");

    assertThatThrownBy(() -> mapper.mapRequestAttributes("queue", input, true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(2);
            });
  }

  @Test
  void listValueMappingMapsEachElement() {
    Map<String, Object> input = Map.of("get", List.of("enabled", "disabled"));

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("GET", List.of("ENABLED", "DISABLED"));
  }

  @Test
  void listValueWithUnknownElementRecordsIssue() {
    Map<String, Object> input = Map.of("get", List.of("enabled", "unknown"));

    assertThatThrownBy(() -> mapper.mapRequestAttributes("queue", input, true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(1);
              assertThat(me.getIssues().get(0).reason()).isEqualTo(MappingReason.UNKNOWN_VALUE);
              assertThat(me.getIssues().get(0).attributeValue()).isEqualTo("unknown");
            });
  }

  @Test
  void nonStringValuePassesThroughUnchanged() {
    Map<String, Object> input = Map.of("get", 42);

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("GET", 42);
  }

  @Test
  void mapResponseListMapsEachObjectWithIndex() {
    Map<String, Object> obj1 = Map.of("MAXDEPTH", 5000);
    Map<String, Object> obj2 = Map.of("MAXDEPTH", 10000);

    List<Map<String, Object>> result = mapper.mapResponseList("queue", List.of(obj1, obj2), false);

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).containsEntry("max_depth", 5000);
    assertThat(result.get(1)).containsEntry("max_depth", 10000);
  }

  @Test
  void mapResponseListStrictThrowsWithAllIssues() {
    Map<String, Object> obj1 = Map.of("UNKNOWN1", "val");
    Map<String, Object> obj2 = Map.of("UNKNOWN2", "val");

    assertThatThrownBy(() -> mapper.mapResponseList("queue", List.of(obj1, obj2), true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(2);
              assertThat(me.getIssues().get(0).objectIndex()).isEqualTo(0);
              assertThat(me.getIssues().get(1).objectIndex()).isEqualTo(1);
            });
  }

  @Test
  void emptyAttributesReturnsEmptyMap() {
    Map<String, Object> result = mapper.mapRequestAttributes("queue", Map.of(), false);

    assertThat(result).isEmpty();
  }

  @Test
  void defaultConstructorLoadsDefaultMappingData() {
    assertThatCode(AttributeMapper::new).doesNotThrowAnyException();
  }

  @Test
  void requestKeyValueMappingWithNonStringValueRecordsIssue() {
    Map<String, Object> input = Map.of("purge", 123);

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("purge", 123);
  }

  @Test
  void listValueWithNonStringElementPassesThrough() {
    Map<String, Object> input = Map.of("get", List.of("enabled", 42, "disabled"));

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    @SuppressWarnings("unchecked")
    List<Object> mapped = (List<Object>) result.get("GET");
    assertThat(mapped).containsExactly("ENABLED", 42, "DISABLED");
  }

  @Test
  void responseListPermissiveWithIssuesReturnsResults() {
    Map<String, Object> obj = Map.of("UNKNOWN", "val");

    List<Map<String, Object>> result = mapper.mapResponseList("queue", List.of(obj), false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("UNKNOWN", "val");
  }

  @Test
  void unknownQualifierPermissiveResponseList() {
    Map<String, Object> obj = Map.of("key", "val");

    List<Map<String, Object>> result = mapper.mapResponseList("nonexistent", List.of(obj), false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("key", "val");
  }

  @Test
  void unknownQualifierStrictResponseListThrows() {
    Map<String, Object> obj = Map.of("key", "val");

    assertThatThrownBy(() -> mapper.mapResponseList("nonexistent", List.of(obj), true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(1);
              assertThat(me.getIssues().get(0).reason()).isEqualTo(MappingReason.UNKNOWN_QUALIFIER);
              assertThat(me.getIssues().get(0).objectIndex()).isEqualTo(0);
            });
  }

  @Test
  void requestKeyValueMappingBothDirections() {
    Map<String, Object> input = Map.of("purge", "no");

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("PURGE", "NO");
  }

  @Test
  void multipleAttributesMappedCorrectly() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("max_depth", 5000);
    input.put("description", "test queue");

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, false);

    assertThat(result).containsEntry("MAXDEPTH", 5000);
    assertThat(result).containsEntry("DESCR", "test queue");
  }

  @Test
  void nullDataThrowsNullPointerException() {
    assertThatThrownBy(() -> new AttributeMapper(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("data");
  }

  @Test
  void responseUnknownKeyStrictThrows() {
    Map<String, Object> input = Map.of("UNKNOWN", "val");

    assertThatThrownBy(() -> mapper.mapResponseAttributes("queue", input, true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(1);
              assertThat(me.getIssues().get(0).reason()).isEqualTo(MappingReason.UNKNOWN_KEY);
              assertThat(me.getIssues().get(0).direction()).isEqualTo(MappingDirection.RESPONSE);
            });
  }

  @Test
  void qualifierWithNoKeyMapsPassesThrough() {
    String json =
        """
        {
          "commands": {},
          "qualifiers": {
            "minimal": {}
          }
        }
        """;
    AttributeMapper minMapper = new AttributeMapper(MappingData.fromJson(json));

    Map<String, Object> input = Map.of("attr", "val");

    Map<String, Object> result = minMapper.mapRequestAttributes("minimal", input, false);

    assertThat(result).containsEntry("attr", "val");
  }

  @Test
  void requestKeyValueWithNonMapEntryRecordsIssue() {
    String json =
        """
        {
          "commands": {},
          "qualifiers": {
            "q": {
              "request_key_map": {},
              "request_key_value_map": {
                "attr": "not_a_map"
              }
            }
          }
        }
        """;
    AttributeMapper testMapper = new AttributeMapper(MappingData.fromJson(json));

    Map<String, Object> result = testMapper.mapRequestAttributes("q", Map.of("attr", "val"), false);

    assertThat(result).containsEntry("attr", "val");
  }

  @Test
  void valueMapWithNonMapEntryPassesThrough() {
    String json =
        """
        {
          "commands": {},
          "qualifiers": {
            "q": {
              "request_key_map": {"attr": "ATTR"},
              "request_value_map": {
                "attr": "not_a_map"
              }
            }
          }
        }
        """;
    AttributeMapper testMapper = new AttributeMapper(MappingData.fromJson(json));

    Map<String, Object> result = testMapper.mapRequestAttributes("q", Map.of("attr", "val"), false);

    assertThat(result).containsEntry("ATTR", "val");
  }

  @Test
  void requestKeyValueWithNonMapTargetRecordsIssue() {
    String json =
        """
        {
          "commands": {},
          "qualifiers": {
            "q": {
              "request_key_map": {},
              "request_key_value_map": {
                "attr": {
                  "val": "not_a_map_target"
                }
              }
            }
          }
        }
        """;
    AttributeMapper testMapper = new AttributeMapper(MappingData.fromJson(json));

    Map<String, Object> result = testMapper.mapRequestAttributes("q", Map.of("attr", "val"), false);

    assertThat(result).containsEntry("attr", "val");
  }

  @Test
  void mapResponseListStrictWithUnknownQualifierThrows() {
    Map<String, Object> obj = Map.of("key", "val");

    assertThatThrownBy(
            () -> mapper.mapResponseList("nonexistent", List.of(obj, Map.of("k", "v")), true))
        .isInstanceOf(MappingException.class)
        .satisfies(
            ex -> {
              MappingException me = (MappingException) ex;
              assertThat(me.getIssues()).hasSize(2);
              assertThat(me.getIssues().get(0).objectIndex()).isEqualTo(0);
              assertThat(me.getIssues().get(1).objectIndex()).isEqualTo(1);
            });
  }

  @Test
  void requestStrictWithNoIssuesSucceeds() {
    Map<String, Object> input = Map.of("max_depth", 5000);

    Map<String, Object> result = mapper.mapRequestAttributes("queue", input, true);

    assertThat(result).containsEntry("MAXDEPTH", 5000);
  }

  @Test
  void responseStrictWithNoIssuesSucceeds() {
    Map<String, Object> input = Map.of("MAXDEPTH", 5000);

    Map<String, Object> result = mapper.mapResponseAttributes("queue", input, true);

    assertThat(result).containsEntry("max_depth", 5000);
  }

  @Test
  void mapResponseListStrictWithNoIssuesSucceeds() {
    Map<String, Object> obj = Map.of("MAXDEPTH", 5000);

    List<Map<String, Object>> result = mapper.mapResponseList("queue", List.of(obj), true);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("max_depth", 5000);
  }
}
