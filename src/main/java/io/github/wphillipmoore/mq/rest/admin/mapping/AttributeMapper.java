package io.github.wphillipmoore.mq.rest.admin.mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Translates attributes between snake_case Python-style names and MQSC parameter names.
 *
 * <p>Implements a 3-layer mapping pipeline mirroring pymqrest's {@code _map_attributes_internal}:
 *
 * <ol>
 *   <li><b>Key-value map</b> (request only): synthetic attributes that map to both a new key and
 *       value
 *   <li><b>Key map</b>: translates attribute names
 *   <li><b>Value map</b>: translates attribute values for specific keys
 * </ol>
 *
 * <p>Supports both strict mode (throws {@link MappingException} on unknown keys/values) and
 * permissive mode (records issues but returns mapped results).
 */
public final class AttributeMapper {

  private final MappingData data;

  /** Creates a mapper using the default built-in mapping data. */
  public AttributeMapper() {
    this(MappingData.loadDefault());
  }

  /**
   * Creates a mapper using the provided mapping data.
   *
   * @param data the mapping data to use, must not be null
   * @throws NullPointerException if data is null
   */
  public AttributeMapper(MappingData data) {
    this.data = Objects.requireNonNull(data, "data");
  }

  /**
   * Maps request attributes from snake_case to MQSC parameter names.
   *
   * @param qualifier the qualifier (e.g., "queue")
   * @param attributes the input attributes to map
   * @param strict if true, throws on mapping issues; if false, passes through unmapped attributes
   * @return mapped attributes
   * @throws MappingException if strict is true and any attributes cannot be mapped
   */
  public Map<String, Object> mapRequestAttributes(
      String qualifier, Map<String, Object> attributes, boolean strict) {
    List<MappingIssue> issues = new ArrayList<>();
    Map<String, Object> result =
        mapAttributes(qualifier, attributes, MappingDirection.REQUEST, null, issues);
    if (strict && !issues.isEmpty()) {
      throw new MappingException(issues);
    }
    return result;
  }

  /**
   * Maps response attributes from MQSC parameter names to snake_case.
   *
   * @param qualifier the qualifier (e.g., "queue")
   * @param attributes the input attributes to map
   * @param strict if true, throws on mapping issues; if false, passes through unmapped attributes
   * @return mapped attributes
   * @throws MappingException if strict is true and any attributes cannot be mapped
   */
  public Map<String, Object> mapResponseAttributes(
      String qualifier, Map<String, Object> attributes, boolean strict) {
    List<MappingIssue> issues = new ArrayList<>();
    Map<String, Object> result =
        mapAttributes(qualifier, attributes, MappingDirection.RESPONSE, null, issues);
    if (strict && !issues.isEmpty()) {
      throw new MappingException(issues);
    }
    return result;
  }

  /**
   * Maps a list of response objects, tracking the object index in any issues.
   *
   * @param qualifier the qualifier (e.g., "queue")
   * @param objects the list of response objects to map
   * @param strict if true, throws after mapping all objects if any issues found
   * @return list of mapped objects
   * @throws MappingException if strict is true and any attributes cannot be mapped
   */
  public List<Map<String, Object>> mapResponseList(
      String qualifier, List<Map<String, Object>> objects, boolean strict) {
    List<MappingIssue> allIssues = new ArrayList<>();
    List<Map<String, Object>> result = new ArrayList<>();
    for (int objectIndex = 0; objectIndex < objects.size(); objectIndex++) {
      result.add(
          mapAttributes(
              qualifier,
              objects.get(objectIndex),
              MappingDirection.RESPONSE,
              objectIndex,
              allIssues));
    }
    if (strict && !allIssues.isEmpty()) {
      throw new MappingException(allIssues);
    }
    return result;
  }

  private Map<String, Object> mapAttributes(
      String qualifier,
      Map<String, Object> attributes,
      MappingDirection direction,
      @Nullable Integer objectIndex,
      List<MappingIssue> issues) {
    Map<String, Object> qualifierData = data.getQualifierData(qualifier);
    if (qualifierData == null) {
      issues.add(
          new MappingIssue(
              direction, MappingReason.UNKNOWN_QUALIFIER, qualifier, null, objectIndex, qualifier));
      return new LinkedHashMap<>(attributes);
    }
    Map<String, String> keyMap =
        getStringMap(
            qualifierData,
            direction == MappingDirection.REQUEST ? "request_key_map" : "response_key_map");
    Map<String, Object> valueMap =
        getNestedMap(
            qualifierData,
            direction == MappingDirection.REQUEST ? "request_value_map" : "response_value_map");
    Map<String, Object> keyValueMap =
        direction == MappingDirection.REQUEST
            ? getNestedMap(qualifierData, "request_key_value_map")
            : Map.of();

    Map<String, Object> result = new LinkedHashMap<>();

    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      String attrName = entry.getKey();
      Object attrValue = entry.getValue();

      // Layer 1: Key-value map (request only)
      if (keyValueMap.containsKey(attrName)) {
        if (attrValue instanceof String) {
          Object mapped = mapKeyValue(keyValueMap, attrName, (String) attrValue);
          if (mapped != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> target = (Map<String, String>) mapped;
            result.put(target.get("key"), target.get("value"));
            continue;
          }
        }
        // Unknown value in key-value map
        issues.add(
            new MappingIssue(
                direction,
                MappingReason.UNKNOWN_VALUE,
                attrName,
                attrValue,
                objectIndex,
                qualifier));
        result.put(attrName, attrValue);
        continue;
      }

      // Layer 2: Key map
      if (!keyMap.containsKey(attrName)) {
        issues.add(
            new MappingIssue(
                direction, MappingReason.UNKNOWN_KEY, attrName, attrValue, objectIndex, qualifier));
        result.put(attrName, attrValue);
        continue;
      }
      String mappedKey = keyMap.get(attrName);

      // Layer 3: Value map
      Object mappedValue =
          mapValue(valueMap, attrName, attrValue, direction, issues, objectIndex, qualifier);
      result.put(mappedKey, mappedValue);
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  private static @Nullable Object mapKeyValue(
      Map<String, Object> keyValueMap, String attrName, String attrValue) {
    Object attrEntry = keyValueMap.get(attrName);
    if (!(attrEntry instanceof Map)) {
      return null;
    }
    Map<String, Object> valueMappings = (Map<String, Object>) attrEntry;
    Object target = valueMappings.get(attrValue);
    return target instanceof Map ? target : null;
  }

  @SuppressWarnings("unchecked")
  private static Object mapValue(
      Map<String, Object> valueMap,
      String attrName,
      Object attrValue,
      MappingDirection direction,
      List<MappingIssue> issues,
      @Nullable Integer objectIndex,
      String qualifier) {
    if (!valueMap.containsKey(attrName)) {
      return attrValue;
    }
    Object mappingEntry = valueMap.get(attrName);
    if (!(mappingEntry instanceof Map)) {
      return attrValue;
    }
    Map<String, String> valueMappings = (Map<String, String>) mappingEntry;

    if (attrValue instanceof String) {
      String mapped = valueMappings.get(attrValue);
      if (mapped != null) {
        return mapped;
      }
      issues.add(
          new MappingIssue(
              direction, MappingReason.UNKNOWN_VALUE, attrName, attrValue, objectIndex, qualifier));
      return attrValue;
    }
    if (attrValue instanceof List) {
      List<Object> listValue = (List<Object>) attrValue;
      List<Object> mappedList = new ArrayList<>();
      for (Object element : listValue) {
        if (element instanceof String) {
          String mapped = valueMappings.get(element);
          if (mapped != null) {
            mappedList.add(mapped);
          } else {
            issues.add(
                new MappingIssue(
                    direction,
                    MappingReason.UNKNOWN_VALUE,
                    attrName,
                    element,
                    objectIndex,
                    qualifier));
            mappedList.add(element);
          }
        } else {
          mappedList.add(element);
        }
      }
      return mappedList;
    }
    return attrValue;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> getStringMap(Map<String, Object> data, String key) {
    Object value = data.get(key);
    if (value instanceof Map) {
      Map<String, String> result = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
        result.put(entry.getKey(), String.valueOf(entry.getValue()));
      }
      return result;
    }
    return Map.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> getNestedMap(Map<String, Object> data, String key) {
    Object value = data.get(key);
    return value instanceof Map ? (Map<String, Object>) value : Map.of();
  }
}
