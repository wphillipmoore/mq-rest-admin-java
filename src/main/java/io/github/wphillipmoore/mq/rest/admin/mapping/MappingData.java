package io.github.wphillipmoore.mq.rest.admin.mapping;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Mapping data for attribute translation between snake_case and MQSC parameter names.
 *
 * <p>Wraps a structured map loaded from JSON (via Gson). Provides typed accessors for {@link
 * AttributeMapper} and supports override merging via {@link MappingOverrideMode}.
 *
 * <p>The JSON structure mirrors pymqrest's {@code MAPPING_DATA} dictionary, containing {@code
 * commands} (command-to-qualifier lookup) and {@code qualifiers} (per-qualifier mapping sub-maps).
 */
public final class MappingData {

  private static final String RESOURCE_NAME = "mapping-data.json";
  private static final Set<String> VALID_TOP_LEVEL_KEYS = Set.of("commands", "qualifiers");
  private static final Gson GSON = new Gson();
  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

  private final Map<String, Object> data;

  private MappingData(Map<String, Object> data) {
    this.data = data;
  }

  /**
   * Loads the default mapping data from the classpath resource.
   *
   * @return mapping data loaded from the built-in resource file
   * @throws IllegalStateException if the resource cannot be found
   */
  public static MappingData loadDefault() {
    return loadFromResource(RESOURCE_NAME);
  }

  /**
   * Loads mapping data from a named classpath resource relative to this class.
   *
   * @param resourceName the resource file name
   * @return mapping data loaded from the resource
   * @throws IllegalStateException if the resource cannot be found
   */
  @SuppressWarnings(
      "PMD.CloseResource") // classpath InputStream closed by InputStreamReader; no leak risk
  static MappingData loadFromResource(String resourceName) {
    InputStream stream = MappingData.class.getResourceAsStream(resourceName);
    if (stream == null) {
      throw new IllegalStateException("Mapping data resource not found: " + resourceName);
    }
    Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
    return new MappingData(GSON.fromJson(reader, MAP_TYPE));
  }

  /**
   * Parses mapping data from a JSON string.
   *
   * @param json the JSON string to parse, must not be null or empty
   * @return mapping data parsed from the JSON
   * @throws NullPointerException if json is null
   * @throws IllegalArgumentException if json is empty or not valid JSON
   */
  public static MappingData fromJson(String json) {
    Objects.requireNonNull(json, "json");
    if (json.isEmpty()) {
      throw new IllegalArgumentException("json must not be empty");
    }
    Map<String, Object> parsed = GSON.fromJson(json, MAP_TYPE);
    if (parsed == null) {
      throw new IllegalArgumentException("json must not be empty");
    }
    return new MappingData(parsed);
  }

  /**
   * Creates mapping data from a programmatic map.
   *
   * @param data the mapping data map, must not be null
   * @return mapping data wrapping the provided map
   * @throws NullPointerException if data is null
   */
  public static MappingData fromMap(Map<String, Object> data) {
    Objects.requireNonNull(data, "data");
    Map<String, Object> copy = deepCopy(data);
    return new MappingData(copy);
  }

  /**
   * Creates new mapping data with overrides applied.
   *
   * @param overrides the override entries to apply, must not be null
   * @param mode the override strategy, must not be null
   * @return new mapping data with overrides applied
   * @throws NullPointerException if overrides or mode is null
   * @throws IllegalArgumentException if overrides contain invalid top-level keys, or if {@link
   *     MappingOverrideMode#REPLACE} mode is used with incomplete overrides
   */
  public MappingData withOverrides(Map<String, Object> overrides, MappingOverrideMode mode) {
    Objects.requireNonNull(overrides, "overrides");
    Objects.requireNonNull(mode, "mode");
    validateTopLevelKeys(overrides);
    if (mode == MappingOverrideMode.REPLACE) {
      return applyReplace(overrides);
    }
    return applyMerge(overrides);
  }

  /**
   * Returns the qualifier name for a given command.
   *
   * @param command the command string (e.g., "DISPLAY QUEUE")
   * @return the qualifier name, or null if the command is unknown
   */
  public @Nullable String getQualifierForCommand(String command) {
    Map<String, Object> commands = getCommandsMap();
    if (commands == null) {
      return null;
    }
    Object entry = commands.get(command);
    if (entry instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> commandMap = (Map<String, Object>) entry;
      Object qualifier = commandMap.get("qualifier");
      return qualifier instanceof String ? (String) qualifier : null;
    }
    return null;
  }

  /**
   * Returns the response parameter macros for a given command.
   *
   * <p>Extracts the {@code response_parameter_macros} list from the command definition. These are
   * shorthand MQSC parameter group names (e.g., {@code "CLUSINFO"}) that can be used in response
   * parameter requests.
   *
   * @param command the MQSC command (e.g., "DISPLAY")
   * @param qualifier the MQSC qualifier (e.g., "QUEUE")
   * @return the list of macro names, or an empty list if the command is unknown or has no macros
   */
  @SuppressWarnings("unchecked")
  public List<String> getResponseParameterMacros(String command, String qualifier) {
    Map<String, Object> commands = getCommandsMap();
    if (commands == null) {
      return List.of();
    }
    String commandKey = command + " " + qualifier;
    Object entry = commands.get(commandKey);
    if (!(entry instanceof Map)) {
      return List.of();
    }
    Map<String, Object> commandMap = (Map<String, Object>) entry;
    Object macros = commandMap.get("response_parameter_macros");
    if (!(macros instanceof List)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : (List<Object>) macros) {
      if (item instanceof String) {
        result.add((String) item);
      }
    }
    return result;
  }

  /**
   * Builds a combined snake_case-to-MQSC lookup map for the given qualifier.
   *
   * <p>Inverts the {@code response_key_map} (MQSC→snake becomes snake→MQSC) and overlays the {@code
   * request_key_map} (snake→MQSC takes precedence). This is used by the session to map WHERE
   * keywords and response parameter names from user-friendly snake_case to MQSC names.
   *
   * @param qualifier the qualifier name (e.g., "queue")
   * @return the combined map, or an empty map if the qualifier is unknown
   */
  @SuppressWarnings("unchecked")
  public Map<String, String> getSnakeToMqscMap(String qualifier) {
    Map<String, Object> qualifierData = getQualifierData(qualifier);
    if (qualifierData == null) {
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    // Start with inverted response_key_map (MQSC→snake → snake→MQSC)
    Object responseKeyMap = qualifierData.get("response_key_map");
    if (responseKeyMap instanceof Map) {
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) responseKeyMap).entrySet()) {
        if (entry.getValue() instanceof String) {
          result.put((String) entry.getValue(), entry.getKey());
        }
      }
    }
    // Overlay with request_key_map (snake→MQSC, takes precedence)
    Object requestKeyMap = qualifierData.get("request_key_map");
    if (requestKeyMap instanceof Map) {
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) requestKeyMap).entrySet()) {
        if (entry.getValue() instanceof String) {
          result.put(entry.getKey(), (String) entry.getValue());
        }
      }
    }
    return result;
  }

  /**
   * Returns the qualifier data map for the given qualifier.
   *
   * @param qualifier the qualifier name (e.g., "queue")
   * @return the qualifier data map, or null if the qualifier is unknown
   */
  @SuppressWarnings("unchecked")
  @Nullable Map<String, Object> getQualifierData(String qualifier) {
    Map<String, Object> qualifiers = getQualifiersMap();
    if (qualifiers == null) {
      return null;
    }
    Object entry = qualifiers.get(qualifier);
    return entry instanceof Map ? (Map<String, Object>) entry : null;
  }

  /**
   * Returns whether the given qualifier exists in the mapping data.
   *
   * @param qualifier the qualifier name to check
   * @return true if the qualifier exists, false otherwise
   */
  public boolean hasQualifier(String qualifier) {
    return getQualifierData(qualifier) != null;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Map<String, Object> getCommandsMap() {
    Object commands = data.get("commands");
    return commands instanceof Map ? (Map<String, Object>) commands : null;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Map<String, Object> getQualifiersMap() {
    Object qualifiers = data.get("qualifiers");
    return qualifiers instanceof Map ? (Map<String, Object>) qualifiers : null;
  }

  private static void validateTopLevelKeys(Map<String, Object> map) {
    for (String key : map.keySet()) {
      if (!VALID_TOP_LEVEL_KEYS.contains(key)) {
        throw new IllegalArgumentException("Invalid top-level key in overrides: " + key);
      }
    }
  }

  private MappingData applyReplace(Map<String, Object> overrides) {
    Map<String, Object> baseCommands = getCommandsMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> overrideCommands =
        overrides.get("commands") instanceof Map
            ? (Map<String, Object>) overrides.get("commands")
            : null;
    if (baseCommands != null
        && !baseCommands.isEmpty()
        && (overrideCommands == null
            || !overrideCommands.keySet().containsAll(baseCommands.keySet()))) {
      throw new IllegalArgumentException("REPLACE overrides must cover all base command keys");
    }

    Map<String, Object> baseQualifiers = getQualifiersMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> overrideQualifiers =
        overrides.get("qualifiers") instanceof Map
            ? (Map<String, Object>) overrides.get("qualifiers")
            : null;
    if (baseQualifiers != null
        && !baseQualifiers.isEmpty()
        && (overrideQualifiers == null
            || !overrideQualifiers.keySet().containsAll(baseQualifiers.keySet()))) {
      throw new IllegalArgumentException("REPLACE overrides must cover all base qualifier keys");
    }
    return new MappingData(deepCopy(overrides));
  }

  private MappingData applyMerge(Map<String, Object> overrides) {
    Map<String, Object> merged = deepCopy(data);
    mergeSection(merged, overrides, "commands");
    mergeSection(merged, overrides, "qualifiers");
    return new MappingData(merged);
  }

  @SuppressWarnings("unchecked")
  private static void mergeSection(
      Map<String, Object> base, Map<String, Object> overrides, String section) {
    Object overrideSection = overrides.get(section);
    if (!(overrideSection instanceof Map)) {
      return;
    }
    Map<String, Object> overrideMap = (Map<String, Object>) overrideSection;
    Object baseSection = base.get(section);
    if (!(baseSection instanceof Map)) {
      base.put(section, deepCopy(overrideMap));
      return;
    }
    Map<String, Object> baseMap = (Map<String, Object>) baseSection;
    for (Map.Entry<String, Object> entry : overrideMap.entrySet()) {
      String key = entry.getKey();
      Object overrideValue = entry.getValue();
      Object baseValue = baseMap.get(key);
      if (baseValue instanceof Map && overrideValue instanceof Map) {
        Map<String, Object> mergedSub = new LinkedHashMap<>((Map<String, Object>) baseValue);
        mergedSub.putAll((Map<String, Object>) overrideValue);
        baseMap.put(key, mergedSub);
      } else {
        baseMap.put(
            key,
            overrideValue instanceof Map
                ? deepCopy((Map<String, Object>) overrideValue)
                : overrideValue);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> deepCopy(Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map) {
        copy.put(entry.getKey(), deepCopy((Map<String, Object>) value));
      } else {
        copy.put(entry.getKey(), value);
      }
    }
    return copy;
  }
}
