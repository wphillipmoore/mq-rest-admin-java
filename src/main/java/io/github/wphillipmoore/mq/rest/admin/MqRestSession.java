package io.github.wphillipmoore.mq.rest.admin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.auth.Credentials;
import io.github.wphillipmoore.mq.rest.admin.auth.LtpaAuth;
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureAction;
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureResult;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestAuthException;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestCommandException;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestResponseException;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestTimeoutException;
import io.github.wphillipmoore.mq.rest.admin.mapping.AttributeMapper;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingData;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingException;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingIssue;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingOverrideMode;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncConfig;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncOperation;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Central session class for the MQ REST administrative API.
 *
 * <p>Manages URL/header building, MQSC command dispatch, response parsing, error detection, and
 * attribute mapping integration. Mirrors pymqrest's {@code MQRESTSession}.
 *
 * <p>Instances are created via the {@link Builder}:
 *
 * <pre>{@code
 * MqRestSession session = new MqRestSession.Builder(
 *         "https://host:9443/ibmmq/rest/v2", "QM1",
 *         new BasicAuth("user", "pass"))
 *     .transport(mockTransport)
 *     .build();
 * }</pre>
 */
public final class MqRestSession {

  static final String DEFAULT_CSRF_TOKEN = "local";
  static final String LTPA_COOKIE_NAME = "LtpaToken2";
  static final String LTPA_LOGIN_PATH = "/login";
  static final String GATEWAY_HEADER = "ibm-mq-rest-gateway-qmgr";

  private static final Map<String, String> DEFAULT_MAPPING_QUALIFIERS =
      Map.ofEntries(
          Map.entry("QUEUE", "queue"),
          Map.entry("QLOCAL", "queue"),
          Map.entry("QREMOTE", "queue"),
          Map.entry("QALIAS", "queue"),
          Map.entry("QMODEL", "queue"),
          Map.entry("QMSTATUS", "qmstatus"),
          Map.entry("QSTATUS", "qstatus"),
          Map.entry("CHANNEL", "channel"),
          Map.entry("QMGR", "qmgr"));

  private static final Gson GSON = new Gson();
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  static final Set<String> RUNNING_VALUES = Set.of("RUNNING", "running");
  static final Set<String> STOPPED_VALUES = Set.of("STOPPED", "stopped");

  private final String restBaseUrl;
  private final String qmgrName;
  private final Credentials credentials;
  private final MqRestTransport transport;
  private final @Nullable String gatewayQmgr;
  private final boolean verifyTls;
  private final @Nullable Duration timeout;
  private final boolean mapAttributes;
  private final boolean mappingStrict;
  private final @Nullable String csrfToken;
  private final MappingData mappingData;
  private final AttributeMapper attributeMapper;

  private Clock clock = new SystemClock();
  private @Nullable String ltpaCookieName;
  private @Nullable String ltpaToken;
  private @Nullable Integer lastHttpStatus;
  private @Nullable String lastResponseText;
  private @Nullable Map<String, Object> lastResponsePayload;
  private @Nullable Map<String, Object> lastCommandPayload;

  private static final ObjectTypeConfig CHANNEL_CONFIG =
      new ObjectTypeConfig(
          "CHANNEL", "CHANNEL", "CHSTATUS", new String[] {"channel_status", "STATUS"}, true);

  private static final ObjectTypeConfig LISTENER_CONFIG =
      new ObjectTypeConfig(
          "LISTENER", "LISTENER", "LSSTATUS", new String[] {"status", "STATUS"}, false);

  private static final ObjectTypeConfig SERVICE_CONFIG =
      new ObjectTypeConfig(
          "SERVICE", "SERVICE", "SVSTATUS", new String[] {"status", "STATUS"}, false);

  /** Clock abstraction for testability. */
  interface Clock {
    void sleep(double seconds) throws InterruptedException;

    double elapsedSeconds();

    void reset();
  }

  /** Real clock using System.nanoTime and Thread.sleep. */
  static final class SystemClock implements Clock {
    private long startNanos;

    SystemClock() {
      reset();
    }

    @Override
    public void sleep(double seconds) throws InterruptedException {
      Thread.sleep((long) (seconds * 1000));
    }

    @Override
    public double elapsedSeconds() {
      return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }

    @Override
    public void reset() {
      startNanos = System.nanoTime();
    }
  }

  private record ObjectTypeConfig(
      String startQualifier,
      String stopQualifier,
      String statusQualifier,
      String[] statusKeys,
      boolean emptyMeansStopped) {}

  /** Package-private setter for test injection. */
  void setClock(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  private MqRestSession(Builder builder) {
    this.restBaseUrl = stripTrailingSlashes(builder.restBaseUrl);
    this.qmgrName = builder.qmgrName;
    this.credentials = builder.credentials;
    this.transport = Objects.requireNonNull(builder.transport, "transport");
    this.gatewayQmgr = builder.gatewayQmgr;
    this.verifyTls = builder.verifyTls;
    this.timeout = builder.timeout;
    this.mapAttributes = builder.mapAttributes;
    this.mappingStrict = builder.mappingStrict;
    this.csrfToken = builder.csrfToken;

    MappingData data = MappingData.loadDefault();
    if (builder.mappingOverrides != null) {
      data = data.withOverrides(builder.mappingOverrides, builder.mappingOverridesMode);
    }
    this.mappingData = data;
    this.attributeMapper = new AttributeMapper(this.mappingData);

    if (credentials instanceof LtpaAuth ltpaAuth) {
      performLtpaLogin(ltpaAuth);
    }
  }

  /** Returns the queue manager name. */
  public String getQmgrName() {
    return qmgrName;
  }

  /** Returns the gateway queue manager name, or {@code null} if not set. */
  public @Nullable String getGatewayQmgr() {
    return gatewayQmgr;
  }

  /** Returns the HTTP status code of the last command, or {@code null} before any command. */
  public @Nullable Integer getLastHttpStatus() {
    return lastHttpStatus;
  }

  /** Returns the raw response text of the last command, or {@code null} before any command. */
  public @Nullable String getLastResponseText() {
    return lastResponseText;
  }

  /**
   * Returns the parsed response payload of the last command, or {@code null} before any command.
   * The returned map is unmodifiable.
   */
  public @Nullable Map<String, Object> getLastResponsePayload() {
    return lastResponsePayload;
  }

  /**
   * Returns the command payload sent in the last command, or {@code null} before any command. The
   * returned map is unmodifiable.
   */
  public @Nullable Map<String, Object> getLastCommandPayload() {
    return lastCommandPayload;
  }

  /**
   * Executes an MQSC command via the MQ REST API.
   *
   * @param command the MQSC command (e.g., "DISPLAY")
   * @param mqscQualifier the MQSC qualifier (e.g., "QUEUE", "QLOCAL")
   * @param name the object name (e.g., queue name), or null
   * @param requestParameters request parameters to send, or null
   * @param responseParameters response parameters to request, or null
   * @param where a WHERE clause string (e.g., "current_q_depth GT 100"), or null
   * @return list of response parameter objects
   */
  public List<Map<String, Object>> mqscCommand(
      String command,
      String mqscQualifier,
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {

    // 1. Normalize command/qualifier to uppercase
    String upperCommand = command.toUpperCase(Locale.ROOT);
    String upperQualifier = mqscQualifier.toUpperCase(Locale.ROOT);

    // 2. Copy requestParameters to mutable map
    requestParameters =
        requestParameters != null ? new LinkedHashMap<>(requestParameters) : new LinkedHashMap<>();

    // 3. Normalize responseParameters
    boolean isDisplay = "DISPLAY".equals(upperCommand);
    responseParameters = normalizeResponseParameters(responseParameters, isDisplay);

    // 4. Resolve mapping qualifier
    String mappingQualifier = resolveMappingQualifier(upperCommand, upperQualifier);

    // 5. Map request attributes if enabled
    if (mapAttributes) {
      requestParameters =
          attributeMapper.mapRequestAttributes(mappingQualifier, requestParameters, mappingStrict);
      responseParameters =
          mapResponseParameters(upperCommand, upperQualifier, mappingQualifier, responseParameters);
    }

    // 6. Map WHERE keyword if provided
    if (where != null && !where.isBlank() && mapAttributes) {
      where = mapWhereKeyword(where, mappingQualifier);
    }
    if (where != null && !where.isBlank()) {
      requestParameters.put("WHERE", where);
    }

    // 7. Build command payload
    Map<String, Object> payload =
        buildCommandPayload(
            upperCommand, upperQualifier, name, requestParameters, responseParameters);
    lastCommandPayload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));

    // 8. Execute transport call
    TransportResponse response =
        transport.postJson(buildMqscUrl(), payload, buildHeaders(), timeout, verifyTls);

    // 9. Save response state
    lastHttpStatus = response.statusCode();
    lastResponseText = response.body();

    // 10. Parse response JSON
    Map<String, Object> responsePayload = parseResponsePayload(response.body());
    lastResponsePayload = Collections.unmodifiableMap(new LinkedHashMap<>(responsePayload));

    // 11. Check for command errors
    raiseForCommandErrors(responsePayload, response.statusCode());

    // 12. Extract commandResponse
    List<Map<String, Object>> commandResponse = extractCommandResponse(responsePayload);

    // 13. Extract parameters from each commandResponse item
    List<Map<String, Object>> parameterObjects = new ArrayList<>();
    for (Map<String, Object> item : commandResponse) {
      Object parameters = item.get("parameters");
      if (parameters instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> parametersMap = (Map<String, Object>) parameters;
        parameterObjects.add(new LinkedHashMap<>(parametersMap));
      } else {
        parameterObjects.add(new LinkedHashMap<>());
      }
    }
    commandResponse = parameterObjects;

    // 14. Flatten nested objects
    commandResponse = flattenNestedObjects(commandResponse);

    // 15. Map response attributes if enabled
    if (mapAttributes) {
      commandResponse = normalizeAndMapResponse(commandResponse, mappingQualifier);
    }

    return commandResponse;
  }

  private String buildMqscUrl() {
    return restBaseUrl + "/admin/action/qmgr/" + qmgrName + "/mqsc";
  }

  private Map<String, String> buildHeaders() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Accept", "application/json");
    if (credentials instanceof BasicAuth basicAuth) {
      headers.put(
          "Authorization", buildBasicAuthHeader(basicAuth.username(), basicAuth.password()));
    } else if (ltpaToken != null) {
      headers.put("Cookie", ltpaCookieName + "=" + ltpaToken);
    }
    // CertificateAuth: no auth header needed (mTLS handled by transport)
    if (csrfToken != null) {
      headers.put("ibm-mq-rest-csrf-token", csrfToken);
    }
    if (gatewayQmgr != null) {
      headers.put(GATEWAY_HEADER, gatewayQmgr);
    }
    return headers;
  }

  static String buildBasicAuthHeader(String username, String password) {
    String credentials = username + ":" + password;
    String encoded =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }

  private void performLtpaLogin(LtpaAuth ltpaAuth) {
    String loginUrl = restBaseUrl + LTPA_LOGIN_PATH;
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("username", ltpaAuth.username());
    payload.put("password", ltpaAuth.password());

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Accept", "application/json");
    if (csrfToken != null) {
      headers.put("ibm-mq-rest-csrf-token", csrfToken);
    }

    TransportResponse response = transport.postJson(loginUrl, payload, headers, timeout, verifyTls);

    if (response.statusCode() >= 400) {
      throw new MqRestAuthException("LTPA login failed", loginUrl, response.statusCode());
    }

    String[] result = extractLtpaToken(response.headers());
    if (result.length == 0) {
      throw new MqRestAuthException(
          "LTPA login succeeded but LtpaToken2 cookie not found in response",
          loginUrl,
          response.statusCode());
    }
    this.ltpaCookieName = result[0];
    this.ltpaToken = result[1];
  }

  static String[] extractLtpaToken(Map<String, String> headers) {
    // Look for Set-Cookie header (case-insensitive)
    String setCookie = null;
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if ("set-cookie".equalsIgnoreCase(entry.getKey())) {
        setCookie = entry.getValue();
        break;
      }
    }
    if (setCookie == null) {
      return new String[0];
    }
    // Parse cookie string for LtpaToken2 (exact or prefixed name)
    for (String part : setCookie.split(";")) {
      String trimmed = part.trim();
      if (trimmed.startsWith(LTPA_COOKIE_NAME)) {
        int eqIndex = trimmed.indexOf('=');
        if (eqIndex > 0) {
          String cookieName = trimmed.substring(0, eqIndex);
          String cookieValue = trimmed.substring(eqIndex + 1);
          return new String[] {cookieName, cookieValue};
        }
      }
    }
    return new String[0];
  }

  String resolveMappingQualifier(String command, String qualifier) {
    // Try command map first
    String commandKey = command + " " + qualifier;
    String fromCommand = mappingData.getQualifierForCommand(commandKey);
    if (fromCommand != null) {
      return fromCommand;
    }
    // Fallback to default mapping qualifiers
    String fromDefault = DEFAULT_MAPPING_QUALIFIERS.get(qualifier);
    if (fromDefault != null) {
      return fromDefault;
    }
    // Last resort: lowercase qualifier
    return qualifier.toLowerCase(Locale.ROOT);
  }

  static Map<String, Object> buildCommandPayload(
      String command,
      String qualifier,
      @Nullable String name,
      Map<String, Object> parameters,
      List<String> responseParameters) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "runCommandJSON");
    payload.put("command", command);
    payload.put("qualifier", qualifier);
    if (name != null && !name.isEmpty()) {
      payload.put("name", name);
    }
    if (!parameters.isEmpty()) {
      payload.put("parameters", new LinkedHashMap<>(parameters));
    }
    if (!responseParameters.isEmpty()) {
      payload.put("responseParameters", new ArrayList<>(responseParameters));
    }
    return payload;
  }

  static List<String> normalizeResponseParameters(
      @Nullable List<String> parameters, boolean isDisplay) {
    if (parameters == null) {
      return isDisplay ? List.of("all") : List.of();
    }
    // Check for "all" (case-insensitive)
    for (String parameter : parameters) {
      if ("all".equalsIgnoreCase(parameter)) {
        return List.of("all");
      }
    }
    return new ArrayList<>(parameters);
  }

  static Map<String, Object> parseResponsePayload(String text) {
    try {
      Object decoded = GSON.fromJson(text, Object.class);
      if (!(decoded instanceof Map)) {
        throw new MqRestResponseException("Response is not a JSON object", text);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) decoded;
      return result;
    } catch (JsonSyntaxException e) {
      throw new MqRestResponseException("Invalid JSON in response", text, e);
    }
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> extractCommandResponse(Map<String, Object> payload) {
    Object commandResponse = payload.get("commandResponse");
    if (commandResponse == null) {
      return new ArrayList<>();
    }
    if (!(commandResponse instanceof List)) {
      throw new MqRestResponseException("commandResponse is not a list", null);
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : (List<Object>) commandResponse) {
      if (!(item instanceof Map)) {
        throw new MqRestResponseException("commandResponse item is not an object", null);
      }
      result.add(new LinkedHashMap<>((Map<String, Object>) item));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  static void raiseForCommandErrors(Map<String, Object> payload, int statusCode) {
    Integer overallCompletionCode = extractOptionalInt(payload.get("overallCompletionCode"));
    Integer overallReasonCode = extractOptionalInt(payload.get("overallReasonCode"));

    boolean hasOverallError = hasErrorCodes(overallCompletionCode, overallReasonCode);

    boolean hasItemError = false;
    Object commandResponse = payload.get("commandResponse");
    if (commandResponse instanceof List) {
      for (Object item : (List<Object>) commandResponse) {
        if (item instanceof Map) {
          Map<String, Object> itemMap = (Map<String, Object>) item;
          Integer completionCode = extractOptionalInt(itemMap.get("completionCode"));
          Integer reasonCode = extractOptionalInt(itemMap.get("reasonCode"));
          if (hasErrorCodes(completionCode, reasonCode)) {
            hasItemError = true;
            break;
          }
        }
      }
    }

    if (hasOverallError || hasItemError) {
      StringBuilder message = new StringBuilder(96);
      message.append("MQSC command error");
      if (overallCompletionCode != null || overallReasonCode != null) {
        message
            .append(" (overallCompletionCode=")
            .append(overallCompletionCode)
            .append(", overallReasonCode=")
            .append(overallReasonCode)
            .append(')');
      }
      throw new MqRestCommandException(message.toString(), payload, statusCode);
    }
  }

  private static boolean hasErrorCodes(
      @Nullable Integer completionCode, @Nullable Integer reasonCode) {
    return (completionCode != null && completionCode != 0)
        || (reasonCode != null && reasonCode != 0);
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> flattenNestedObjects(
      List<Map<String, Object>> parameterObjects) {
    List<Map<String, Object>> flattened = new ArrayList<>();
    for (Map<String, Object> item : parameterObjects) {
      Object objects = item.get("objects");
      if (objects instanceof List) {
        Map<String, Object> shared = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : item.entrySet()) {
          if (!"objects".equals(entry.getKey())) {
            shared.put(entry.getKey(), entry.getValue());
          }
        }
        for (Object nested : (List<Object>) objects) {
          if (nested instanceof Map) {
            Map<String, Object> merged = new LinkedHashMap<>(shared);
            merged.putAll((Map<String, Object>) nested);
            flattened.add(merged);
          }
        }
      } else {
        flattened.add(item);
      }
    }
    return flattened;
  }

  static Map<String, Object> normalizeResponseAttributes(Map<String, Object> attributes) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      normalized.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
    }
    return normalized;
  }

  private List<Map<String, Object>> normalizeAndMapResponse(
      List<Map<String, Object>> commandResponse, String mappingQualifier) {
    List<Map<String, Object>> normalized = new ArrayList<>();
    for (Map<String, Object> item : commandResponse) {
      normalized.add(normalizeResponseAttributes(item));
    }
    return attributeMapper.mapResponseList(mappingQualifier, normalized, mappingStrict);
  }

  private List<String> mapResponseParameters(
      String command,
      String mqscQualifier,
      String mappingQualifier,
      List<String> responseParameters) {
    // "all" passes through unmapped
    if (responseParameters.size() == 1 && "all".equalsIgnoreCase(responseParameters.get(0))) {
      return responseParameters;
    }

    List<String> macros = mappingData.getResponseParameterMacros(command, mqscQualifier);
    // Build case-insensitive macro lookup
    Map<String, String> macroLookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (String macro : macros) {
      macroLookup.put(macro, macro);
    }

    Map<String, String> snakeToMqsc = mappingData.getSnakeToMqscMap(mappingQualifier);

    List<MappingIssue> issues = new ArrayList<>();
    List<String> mapped = new ArrayList<>();
    for (String param : responseParameters) {
      // Check macros first (case-insensitive)
      String macroMatch = macroLookup.get(param);
      if (macroMatch != null) {
        mapped.add(macroMatch);
        continue;
      }
      // Check combined snake→MQSC map
      String mqscName = snakeToMqsc.get(param);
      if (mqscName != null) {
        mapped.add(mqscName);
        continue;
      }
      // Unknown parameter
      issues.add(
          new MappingIssue(
              io.github.wphillipmoore.mq.rest.admin.mapping.MappingDirection.REQUEST,
              io.github.wphillipmoore.mq.rest.admin.mapping.MappingReason.UNKNOWN_KEY,
              param,
              null,
              null,
              mappingQualifier));
      mapped.add(param);
    }

    if (mappingStrict && !issues.isEmpty()) {
      throw new MappingException(issues);
    }
    return mapped;
  }

  String mapWhereKeyword(String where, String mappingQualifier) {
    // Split on first whitespace
    int spaceIdx = indexOfFirstWhitespace(where);
    String keyword = spaceIdx < 0 ? where : where.substring(0, spaceIdx);
    String rest = spaceIdx < 0 ? "" : where.substring(spaceIdx + 1);

    Map<String, String> snakeToMqsc = mappingData.getSnakeToMqscMap(mappingQualifier);
    if (snakeToMqsc.isEmpty() && !mappingData.hasQualifier(mappingQualifier)) {
      // Unknown qualifier
      if (mappingStrict) {
        throw new MappingException(
            List.of(
                new MappingIssue(
                    io.github.wphillipmoore.mq.rest.admin.mapping.MappingDirection.REQUEST,
                    io.github.wphillipmoore.mq.rest.admin.mapping.MappingReason.UNKNOWN_QUALIFIER,
                    keyword,
                    null,
                    null,
                    mappingQualifier)));
      }
      return where;
    }

    String mappedKeyword = snakeToMqsc.get(keyword);
    if (mappedKeyword == null) {
      if (mappingStrict) {
        throw new MappingException(
            List.of(
                new MappingIssue(
                    io.github.wphillipmoore.mq.rest.admin.mapping.MappingDirection.REQUEST,
                    io.github.wphillipmoore.mq.rest.admin.mapping.MappingReason.UNKNOWN_KEY,
                    keyword,
                    null,
                    null,
                    mappingQualifier)));
      }
      return where;
    }

    if (!rest.isEmpty()) {
      return mappedKeyword + " " + rest;
    }
    return mappedKeyword;
  }

  private static int indexOfFirstWhitespace(String s) {
    for (int charIndex = 0; charIndex < s.length(); charIndex++) {
      if (Character.isWhitespace(s.charAt(charIndex))) {
        return charIndex;
      }
    }
    return -1;
  }

  static @Nullable Integer extractOptionalInt(@Nullable Object value) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    return null;
  }

  private static String stripTrailingSlashes(String url) {
    while (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    return url;
  }

  // BEGIN GENERATED MQSC METHODS
  /** Executes an ALTER AUTHINFO MQSC command. */
  public void alterAuthinfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "AUTHINFO", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER BUFFPOOL MQSC command. */
  public void alterBuffpool(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "BUFFPOOL", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER CFSTRUCT MQSC command. */
  public void alterCfstruct(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "CFSTRUCT", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER CHANNEL MQSC command. */
  public void alterChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER COMMINFO MQSC command. */
  public void alterComminfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "COMMINFO", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER LISTENER MQSC command. */
  public void alterListener(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "LISTENER", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER NAMELIST MQSC command. */
  public void alterNamelist(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "NAMELIST", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER PROCESS MQSC command. */
  public void alterProcess(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "PROCESS", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER PSID MQSC command. */
  public void alterPsid(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "PSID", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER QALIAS MQSC command. */
  public void alterQalias(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "QALIAS", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER QLOCAL MQSC command. */
  public void alterQlocal(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "QLOCAL", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER QMGR MQSC command. */
  public void alterQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "QMGR", null, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER QMODEL MQSC command. */
  public void alterQmodel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "QMODEL", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER QREMOTE MQSC command. */
  public void alterQremote(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "QREMOTE", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER SECURITY MQSC command. */
  public void alterSecurity(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "SECURITY", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER SERVICE MQSC command. */
  public void alterService(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "SERVICE", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER SMDS MQSC command. */
  public void alterSmds(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "SMDS", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER STGCLASS MQSC command. */
  public void alterStgclass(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "STGCLASS", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER SUB MQSC command. */
  public void alterSub(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "SUB", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER TOPIC MQSC command. */
  public void alterTopic(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "TOPIC", name, requestParameters, responseParameters, null);
  }

  /** Executes an ALTER TRACE MQSC command. */
  public void alterTrace(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ALTER", "TRACE", name, requestParameters, responseParameters, null);
  }

  /** Executes an ARCHIVE LOG MQSC command. */
  public void archiveLog(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("ARCHIVE", "LOG", name, requestParameters, responseParameters, null);
  }

  /** Executes a BACKUP CFSTRUCT MQSC command. */
  public void backupCfstruct(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("BACKUP", "CFSTRUCT", name, requestParameters, responseParameters, null);
  }

  /** Executes a CLEAR QLOCAL MQSC command. */
  public void clearQlocal(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("CLEAR", "QLOCAL", name, requestParameters, responseParameters, null);
  }

  /** Executes a CLEAR TOPICSTR MQSC command. */
  public void clearTopicstr(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("CLEAR", "TOPICSTR", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE AUTHINFO MQSC command. */
  public void defineAuthinfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "AUTHINFO", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE BUFFPOOL MQSC command. */
  public void defineBuffpool(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "BUFFPOOL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE CFSTRUCT MQSC command. */
  public void defineCfstruct(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "CFSTRUCT", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE CHANNEL MQSC command. */
  public void defineChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DEFINE", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE COMMINFO MQSC command. */
  public void defineComminfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "COMMINFO", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE LISTENER MQSC command. */
  public void defineListener(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "LISTENER", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE LOG MQSC command. */
  public void defineLog(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "LOG", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE MAXSMSGS MQSC command. */
  public void defineMaxsmsgs(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "MAXSMSGS", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE NAMELIST MQSC command. */
  public void defineNamelist(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "NAMELIST", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE PROCESS MQSC command. */
  public void defineProcess(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "PROCESS", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE PSID MQSC command. */
  public void definePsid(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "PSID", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE QALIAS MQSC command. */
  public void defineQalias(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DEFINE", "QALIAS", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE QLOCAL MQSC command. */
  public void defineQlocal(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DEFINE", "QLOCAL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE QMODEL MQSC command. */
  public void defineQmodel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DEFINE", "QMODEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE QREMOTE MQSC command. */
  public void defineQremote(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DEFINE", "QREMOTE", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE SERVICE MQSC command. */
  public void defineService(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "SERVICE", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE STGCLASS MQSC command. */
  public void defineStgclass(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "STGCLASS", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE SUB MQSC command. */
  public void defineSub(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "SUB", name, requestParameters, responseParameters, null);
  }

  /** Executes a DEFINE TOPIC MQSC command. */
  public void defineTopic(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DEFINE", "TOPIC", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE AUTHINFO MQSC command. */
  public void deleteAuthinfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "AUTHINFO", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE AUTHREC MQSC command. */
  public void deleteAuthrec(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "AUTHREC", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE BUFFPOOL MQSC command. */
  public void deleteBuffpool(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "BUFFPOOL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE CFSTRUCT MQSC command. */
  public void deleteCfstruct(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "CFSTRUCT", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE CHANNEL MQSC command. */
  public void deleteChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DELETE", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE COMMINFO MQSC command. */
  public void deleteComminfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "COMMINFO", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE LISTENER MQSC command. */
  public void deleteListener(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "LISTENER", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE NAMELIST MQSC command. */
  public void deleteNamelist(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "NAMELIST", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE POLICY MQSC command. */
  public void deletePolicy(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "POLICY", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE PROCESS MQSC command. */
  public void deleteProcess(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "PROCESS", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE PSID MQSC command. */
  public void deletePsid(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "PSID", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE QALIAS MQSC command. */
  public void deleteQalias(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DELETE", "QALIAS", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE QLOCAL MQSC command. */
  public void deleteQlocal(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DELETE", "QLOCAL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE QMODEL MQSC command. */
  public void deleteQmodel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DELETE", "QMODEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE QREMOTE MQSC command. */
  public void deleteQremote(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DELETE", "QREMOTE", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE QUEUE MQSC command. */
  public void deleteQueue(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    Objects.requireNonNull(name, "name");
    mqscCommand("DELETE", "QUEUE", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE SERVICE MQSC command. */
  public void deleteService(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "SERVICE", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE STGCLASS MQSC command. */
  public void deleteStgclass(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "STGCLASS", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE SUB MQSC command. */
  public void deleteSub(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "SUB", name, requestParameters, responseParameters, null);
  }

  /** Executes a DELETE TOPIC MQSC command. */
  public void deleteTopic(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("DELETE", "TOPIC", name, requestParameters, responseParameters, null);
  }

  /** Executes a DISPLAY APSTATUS MQSC command. */
  public List<Map<String, Object>> displayApstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "APSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY ARCHIVE MQSC command. */
  public List<Map<String, Object>> displayArchive(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "ARCHIVE", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY AUTHINFO MQSC command. */
  public List<Map<String, Object>> displayAuthinfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "AUTHINFO", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY AUTHREC MQSC command. */
  public List<Map<String, Object>> displayAuthrec(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "AUTHREC", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY AUTHSERV MQSC command. */
  public List<Map<String, Object>> displayAuthserv(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "AUTHSERV", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CFSTATUS MQSC command. */
  public List<Map<String, Object>> displayCfstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "CFSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CFSTRUCT MQSC command. */
  public List<Map<String, Object>> displayCfstruct(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "CFSTRUCT", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CHANNEL MQSC command. */
  public List<Map<String, Object>> displayChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand(
        "DISPLAY",
        "CHANNEL",
        name != null ? name : "*",
        requestParameters,
        responseParameters,
        where);
  }

  /** Executes a DISPLAY CHINIT MQSC command. */
  public List<Map<String, Object>> displayChinit(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "CHINIT", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CHLAUTH MQSC command. */
  public List<Map<String, Object>> displayChlauth(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "CHLAUTH", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CHSTATUS MQSC command. */
  public List<Map<String, Object>> displayChstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "CHSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CLUSQMGR MQSC command. */
  public List<Map<String, Object>> displayClusqmgr(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "CLUSQMGR", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CMDSERV MQSC command. */
  public @Nullable Map<String, Object> displayCmdserv(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    List<Map<String, Object>> objects =
        mqscCommand("DISPLAY", "CMDSERV", null, requestParameters, responseParameters, null);
    return objects.isEmpty() ? null : objects.get(0);
  }

  /** Executes a DISPLAY COMMINFO MQSC command. */
  public List<Map<String, Object>> displayComminfo(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "COMMINFO", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY CONN MQSC command. */
  public List<Map<String, Object>> displayConn(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "CONN", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY ENTAUTH MQSC command. */
  public List<Map<String, Object>> displayEntauth(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "ENTAUTH", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY GROUP MQSC command. */
  public List<Map<String, Object>> displayGroup(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "GROUP", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY LISTENER MQSC command. */
  public List<Map<String, Object>> displayListener(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "LISTENER", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY LOG MQSC command. */
  public List<Map<String, Object>> displayLog(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "LOG", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY LSSTATUS MQSC command. */
  public List<Map<String, Object>> displayLsstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "LSSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY MAXSMSGS MQSC command. */
  public List<Map<String, Object>> displayMaxsmsgs(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "MAXSMSGS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY NAMELIST MQSC command. */
  public List<Map<String, Object>> displayNamelist(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "NAMELIST", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY POLICY MQSC command. */
  public List<Map<String, Object>> displayPolicy(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "POLICY", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY PROCESS MQSC command. */
  public List<Map<String, Object>> displayProcess(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "PROCESS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY PUBSUB MQSC command. */
  public List<Map<String, Object>> displayPubsub(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "PUBSUB", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY QMGR MQSC command. */
  public @Nullable Map<String, Object> displayQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    List<Map<String, Object>> objects =
        mqscCommand("DISPLAY", "QMGR", null, requestParameters, responseParameters, null);
    return objects.isEmpty() ? null : objects.get(0);
  }

  /** Executes a DISPLAY QMSTATUS MQSC command. */
  public @Nullable Map<String, Object> displayQmstatus(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    List<Map<String, Object>> objects =
        mqscCommand("DISPLAY", "QMSTATUS", null, requestParameters, responseParameters, null);
    return objects.isEmpty() ? null : objects.get(0);
  }

  /** Executes a DISPLAY QSTATUS MQSC command. */
  public List<Map<String, Object>> displayQstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "QSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY QUEUE MQSC command. */
  public List<Map<String, Object>> displayQueue(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand(
        "DISPLAY",
        "QUEUE",
        name != null ? name : "*",
        requestParameters,
        responseParameters,
        where);
  }

  /** Executes a DISPLAY SBSTATUS MQSC command. */
  public List<Map<String, Object>> displaySbstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SBSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY SECURITY MQSC command. */
  public List<Map<String, Object>> displaySecurity(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SECURITY", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY SERVICE MQSC command. */
  public List<Map<String, Object>> displayService(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SERVICE", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY SMDS MQSC command. */
  public List<Map<String, Object>> displaySmds(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SMDS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY SMDSCONN MQSC command. */
  public List<Map<String, Object>> displaySmdsconn(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SMDSCONN", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY STGCLASS MQSC command. */
  public List<Map<String, Object>> displayStgclass(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "STGCLASS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY SUB MQSC command. */
  public List<Map<String, Object>> displaySub(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SUB", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY SVSTATUS MQSC command. */
  public List<Map<String, Object>> displaySvstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SVSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY SYSTEM MQSC command. */
  public List<Map<String, Object>> displaySystem(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "SYSTEM", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY TCLUSTER MQSC command. */
  public List<Map<String, Object>> displayTcluster(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "TCLUSTER", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY THREAD MQSC command. */
  public List<Map<String, Object>> displayThread(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "THREAD", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY TOPIC MQSC command. */
  public List<Map<String, Object>> displayTopic(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "TOPIC", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY TPSTATUS MQSC command. */
  public List<Map<String, Object>> displayTpstatus(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "TPSTATUS", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY TRACE MQSC command. */
  public List<Map<String, Object>> displayTrace(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "TRACE", name, requestParameters, responseParameters, where);
  }

  /** Executes a DISPLAY USAGE MQSC command. */
  public List<Map<String, Object>> displayUsage(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters,
      @Nullable String where) {
    return mqscCommand("DISPLAY", "USAGE", name, requestParameters, responseParameters, where);
  }

  /** Executes a MOVE QLOCAL MQSC command. */
  public void moveQlocal(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("MOVE", "QLOCAL", name, requestParameters, responseParameters, null);
  }

  /** Executes a PING CHANNEL MQSC command. */
  public void pingChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("PING", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a PING QMGR MQSC command. */
  public void pingQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("PING", "QMGR", null, requestParameters, responseParameters, null);
  }

  /** Executes a PURGE CHANNEL MQSC command. */
  public void purgeChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("PURGE", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a RECOVER BSDS MQSC command. */
  public void recoverBsds(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RECOVER", "BSDS", name, requestParameters, responseParameters, null);
  }

  /** Executes a RECOVER CFSTRUCT MQSC command. */
  public void recoverCfstruct(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RECOVER", "CFSTRUCT", name, requestParameters, responseParameters, null);
  }

  /** Executes a REFRESH CLUSTER MQSC command. */
  public void refreshCluster(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("REFRESH", "CLUSTER", name, requestParameters, responseParameters, null);
  }

  /** Executes a REFRESH QMGR MQSC command. */
  public void refreshQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("REFRESH", "QMGR", null, requestParameters, responseParameters, null);
  }

  /** Executes a REFRESH SECURITY MQSC command. */
  public void refreshSecurity(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("REFRESH", "SECURITY", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESET CFSTRUCT MQSC command. */
  public void resetCfstruct(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESET", "CFSTRUCT", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESET CHANNEL MQSC command. */
  public void resetChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESET", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESET CLUSTER MQSC command. */
  public void resetCluster(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESET", "CLUSTER", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESET QMGR MQSC command. */
  public void resetQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("RESET", "QMGR", null, requestParameters, responseParameters, null);
  }

  /** Executes a RESET QSTATS MQSC command. */
  public void resetQstats(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESET", "QSTATS", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESET SMDS MQSC command. */
  public void resetSmds(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESET", "SMDS", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESET TPIPE MQSC command. */
  public void resetTpipe(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESET", "TPIPE", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESOLVE CHANNEL MQSC command. */
  public void resolveChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESOLVE", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESOLVE INDOUBT MQSC command. */
  public void resolveIndoubt(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RESOLVE", "INDOUBT", name, requestParameters, responseParameters, null);
  }

  /** Executes a RESUME QMGR MQSC command. */
  public void resumeQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("RESUME", "QMGR", null, requestParameters, responseParameters, null);
  }

  /** Executes a RVERIFY SECURITY MQSC command. */
  public void rverifySecurity(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("RVERIFY", "SECURITY", name, requestParameters, responseParameters, null);
  }

  /** Executes a SET ARCHIVE MQSC command. */
  public void setArchive(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("SET", "ARCHIVE", name, requestParameters, responseParameters, null);
  }

  /** Executes a SET AUTHREC MQSC command. */
  public void setAuthrec(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("SET", "AUTHREC", name, requestParameters, responseParameters, null);
  }

  /** Executes a SET CHLAUTH MQSC command. */
  public void setChlauth(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("SET", "CHLAUTH", name, requestParameters, responseParameters, null);
  }

  /** Executes a SET LOG MQSC command. */
  public void setLog(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("SET", "LOG", name, requestParameters, responseParameters, null);
  }

  /** Executes a SET POLICY MQSC command. */
  public void setPolicy(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("SET", "POLICY", name, requestParameters, responseParameters, null);
  }

  /** Executes a SET SYSTEM MQSC command. */
  public void setSystem(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("SET", "SYSTEM", name, requestParameters, responseParameters, null);
  }

  /** Executes a START CHANNEL MQSC command. */
  public void startChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("START", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a START CHINIT MQSC command. */
  public void startChinit(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("START", "CHINIT", name, requestParameters, responseParameters, null);
  }

  /** Executes a START CMDSERV MQSC command. */
  public void startCmdserv(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("START", "CMDSERV", null, requestParameters, responseParameters, null);
  }

  /** Executes a START LISTENER MQSC command. */
  public void startListener(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("START", "LISTENER", name, requestParameters, responseParameters, null);
  }

  /** Executes a START QMGR MQSC command. */
  public void startQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("START", "QMGR", null, requestParameters, responseParameters, null);
  }

  /** Executes a START SERVICE MQSC command. */
  public void startService(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("START", "SERVICE", name, requestParameters, responseParameters, null);
  }

  /** Executes a START SMDSCONN MQSC command. */
  public void startSmdsconn(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("START", "SMDSCONN", name, requestParameters, responseParameters, null);
  }

  /** Executes a START TRACE MQSC command. */
  public void startTrace(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("START", "TRACE", name, requestParameters, responseParameters, null);
  }

  /** Executes a STOP CHANNEL MQSC command. */
  public void stopChannel(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "CHANNEL", name, requestParameters, responseParameters, null);
  }

  /** Executes a STOP CHINIT MQSC command. */
  public void stopChinit(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "CHINIT", name, requestParameters, responseParameters, null);
  }

  /** Executes a STOP CMDSERV MQSC command. */
  public void stopCmdserv(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "CMDSERV", null, requestParameters, responseParameters, null);
  }

  /** Executes a STOP CONN MQSC command. */
  public void stopConn(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "CONN", name, requestParameters, responseParameters, null);
  }

  /** Executes a STOP LISTENER MQSC command. */
  public void stopListener(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "LISTENER", name, requestParameters, responseParameters, null);
  }

  /** Executes a STOP QMGR MQSC command. */
  public void stopQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "QMGR", null, requestParameters, responseParameters, null);
  }

  /** Executes a STOP SERVICE MQSC command. */
  public void stopService(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "SERVICE", name, requestParameters, responseParameters, null);
  }

  /** Executes a STOP SMDSCONN MQSC command. */
  public void stopSmdsconn(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "SMDSCONN", name, requestParameters, responseParameters, null);
  }

  /** Executes a STOP TRACE MQSC command. */
  public void stopTrace(
      @Nullable String name,
      @Nullable Map<String, Object> requestParameters,
      @Nullable List<String> responseParameters) {
    mqscCommand("STOP", "TRACE", name, requestParameters, responseParameters, null);
  }

  /** Executes a SUSPEND QMGR MQSC command. */
  public void suspendQmgr(
      @Nullable Map<String, Object> requestParameters, @Nullable List<String> responseParameters) {
    mqscCommand("SUSPEND", "QMGR", null, requestParameters, responseParameters, null);
  }

  // END GENERATED MQSC METHODS

  // ---------------------------------------------------------------------------
  // Ensure methods — idempotent upsert operations
  // ---------------------------------------------------------------------------

  /**
   * Compares two values for equality using string conversion, trimming, and case-insensitive
   * comparison.
   *
   * <p>Mirrors pymqrest's {@code _values_match()}.
   *
   * @param desired the desired value
   * @param current the current value from the queue manager
   * @return true if the values are considered equal
   */
  static boolean valuesMatch(Object desired, @Nullable Object current) {
    return current != null
        && String.valueOf(desired).strip().equalsIgnoreCase(String.valueOf(current).strip());
  }

  /**
   * Extracts the parameters map from a commandResponse item.
   *
   * <p>The MQ REST API wraps attributes in a {@code "parameters"} sub-object. This method unwraps
   * that, mirroring what pymqrest does in {@code _mqsc_command()}.
   *
   * @param item a commandResponse item
   * @return the parameters map, or the item itself if no parameters sub-object exists
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> extractParametersMap(Map<String, Object> item) {
    Object parameters = item.get("parameters");
    if (parameters instanceof Map) {
      return new LinkedHashMap<>((Map<String, Object>) parameters);
    }
    return item;
  }

  private EnsureResult ensureObject(
      String name,
      @Nullable Map<String, Object> requestParameters,
      String displayQualifier,
      String defineQualifier,
      String alterQualifier) {

    // 1. Try to DISPLAY the object
    List<Map<String, Object>> currentObjects;
    try {
      currentObjects = mqscCommand("DISPLAY", displayQualifier, name, null, List.of("all"), null);
    } catch (MqRestCommandException e) {
      currentObjects = List.of();
    }

    // 2. Object not found → DEFINE
    if (currentObjects.isEmpty()) {
      mqscCommand("DEFINE", defineQualifier, name, requestParameters, null, null);
      return new EnsureResult(EnsureAction.CREATED, null);
    }

    // 3. No params → UNCHANGED
    if (requestParameters == null || requestParameters.isEmpty()) {
      return new EnsureResult(EnsureAction.UNCHANGED, null);
    }

    // 4. Compare each desired attribute against current
    Map<String, Object> current = extractParametersMap(currentObjects.get(0));
    List<String> changedKeys = new ArrayList<>();
    Map<String, Object> changedParams = new LinkedHashMap<>();

    for (Map.Entry<String, Object> entry : requestParameters.entrySet()) {
      Object currentValue = current.get(entry.getKey());
      if (!valuesMatch(entry.getValue(), currentValue)) {
        changedKeys.add(entry.getKey());
        changedParams.put(entry.getKey(), entry.getValue());
      }
    }

    // 5. All match → UNCHANGED
    if (changedKeys.isEmpty()) {
      return new EnsureResult(EnsureAction.UNCHANGED, null);
    }

    // 6. Some differ → ALTER with only changed attrs
    mqscCommand("ALTER", alterQualifier, name, changedParams, null, null);
    return new EnsureResult(EnsureAction.UPDATED, changedKeys);
  }

  /**
   * Ensures the queue manager attributes match the desired values.
   *
   * <p>Unlike other ensure methods, the queue manager always exists so this never returns {@link
   * EnsureAction#CREATED}.
   *
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureQmgr(@Nullable Map<String, Object> requestParameters) {
    // No params → UNCHANGED (skip DISPLAY)
    if (requestParameters == null || requestParameters.isEmpty()) {
      return new EnsureResult(EnsureAction.UNCHANGED, null);
    }

    // DISPLAY QMGR
    List<Map<String, Object>> currentObjects =
        mqscCommand("DISPLAY", "QMGR", null, null, List.of("all"), null);

    Map<String, Object> current =
        currentObjects.isEmpty() ? Map.of() : extractParametersMap(currentObjects.get(0));
    List<String> changedKeys = new ArrayList<>();
    Map<String, Object> changedParams = new LinkedHashMap<>();

    for (Map.Entry<String, Object> entry : requestParameters.entrySet()) {
      Object currentValue = current.get(entry.getKey());
      if (!valuesMatch(entry.getValue(), currentValue)) {
        changedKeys.add(entry.getKey());
        changedParams.put(entry.getKey(), entry.getValue());
      }
    }

    if (changedKeys.isEmpty()) {
      return new EnsureResult(EnsureAction.UNCHANGED, null);
    }

    mqscCommand("ALTER", "QMGR", null, changedParams, null, null);
    return new EnsureResult(EnsureAction.UPDATED, changedKeys);
  }

  /**
   * Ensures a local queue exists with the desired attributes.
   *
   * @param name the queue name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureQlocal(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "QUEUE", "QLOCAL", "QLOCAL");
  }

  /**
   * Ensures a remote queue exists with the desired attributes.
   *
   * @param name the queue name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureQremote(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "QUEUE", "QREMOTE", "QREMOTE");
  }

  /**
   * Ensures an alias queue exists with the desired attributes.
   *
   * @param name the queue name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureQalias(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "QUEUE", "QALIAS", "QALIAS");
  }

  /**
   * Ensures a model queue exists with the desired attributes.
   *
   * @param name the queue name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureQmodel(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "QUEUE", "QMODEL", "QMODEL");
  }

  /**
   * Ensures a channel exists with the desired attributes.
   *
   * @param name the channel name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureChannel(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "CHANNEL", "CHANNEL", "CHANNEL");
  }

  /**
   * Ensures an authentication information object exists with the desired attributes.
   *
   * @param name the authinfo name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureAuthinfo(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "AUTHINFO", "AUTHINFO", "AUTHINFO");
  }

  /**
   * Ensures a listener exists with the desired attributes.
   *
   * @param name the listener name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureListener(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "LISTENER", "LISTENER", "LISTENER");
  }

  /**
   * Ensures a namelist exists with the desired attributes.
   *
   * @param name the namelist name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureNamelist(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "NAMELIST", "NAMELIST", "NAMELIST");
  }

  /**
   * Ensures a process exists with the desired attributes.
   *
   * @param name the process name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureProcess(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "PROCESS", "PROCESS", "PROCESS");
  }

  /**
   * Ensures a service exists with the desired attributes.
   *
   * @param name the service name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureService(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "SERVICE", "SERVICE", "SERVICE");
  }

  /**
   * Ensures a topic exists with the desired attributes.
   *
   * @param name the topic name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureTopic(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "TOPIC", "TOPIC", "TOPIC");
  }

  /**
   * Ensures a subscription exists with the desired attributes.
   *
   * @param name the subscription name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureSub(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "SUB", "SUB", "SUB");
  }

  /**
   * Ensures a storage class exists with the desired attributes.
   *
   * @param name the storage class name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureStgclass(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "STGCLASS", "STGCLASS", "STGCLASS");
  }

  /**
   * Ensures a communication information object exists with the desired attributes.
   *
   * @param name the comminfo name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureComminfo(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "COMMINFO", "COMMINFO", "COMMINFO");
  }

  /**
   * Ensures a coupling facility structure exists with the desired attributes.
   *
   * @param name the CF structure name
   * @param requestParameters the desired attributes, or null
   * @return the result indicating what action was taken
   */
  public EnsureResult ensureCfstruct(String name, @Nullable Map<String, Object> requestParameters) {
    return ensureObject(name, requestParameters, "CFSTRUCT", "CFSTRUCT", "CFSTRUCT");
  }

  // ---------------------------------------------------------------------------
  // Sync methods — start/stop/restart with polling
  // ---------------------------------------------------------------------------

  /**
   * Checks whether any row contains a status value matching the target set.
   *
   * @param rows raw commandResponse items (with {@code parameters} wrapper)
   * @param statusKeys the attribute keys to check for status values
   * @param targetValues the set of acceptable status values
   * @return true if a matching status value is found
   */
  static boolean hasStatus(
      List<Map<String, Object>> rows, String[] statusKeys, Set<String> targetValues) {
    for (Map<String, Object> row : rows) {
      Map<String, Object> parameters = extractParametersMap(row);
      for (String key : statusKeys) {
        Object value = parameters.get(key);
        if (value instanceof String s && targetValues.contains(s)) {
          return true;
        }
      }
    }
    return false;
  }

  private SyncResult startAndPoll(String name, ObjectTypeConfig config, SyncConfig syncConfig) {
    // Issue START command
    mqscCommand("START", config.startQualifier(), name, null, null, null);

    // Poll for RUNNING status
    clock.reset();
    int polls = 0;
    while (true) {
      try {
        clock.sleep(syncConfig.pollIntervalSeconds());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new MqRestTimeoutException(
            "Interrupted while polling for start of " + name,
            name,
            "START",
            clock.elapsedSeconds(),
            e);
      }

      List<Map<String, Object>> statusRows;
      try {
        statusRows =
            mqscCommand("DISPLAY", config.statusQualifier(), name, null, List.of("all"), null);
      } catch (MqRestCommandException e) {
        statusRows = List.of();
      }
      polls++;

      if (hasStatus(statusRows, config.statusKeys(), RUNNING_VALUES)) {
        return new SyncResult(SyncOperation.STARTED, polls, clock.elapsedSeconds());
      }

      if (clock.elapsedSeconds() >= syncConfig.timeoutSeconds()) {
        throw new MqRestTimeoutException(
            "Timed out waiting for start of " + name, name, "START", clock.elapsedSeconds());
      }
    }
  }

  private SyncResult stopAndPoll(String name, ObjectTypeConfig config, SyncConfig syncConfig) {
    // Issue STOP command
    mqscCommand("STOP", config.stopQualifier(), name, null, null, null);

    // Poll for STOPPED status
    clock.reset();
    int polls = 0;
    while (true) {
      try {
        clock.sleep(syncConfig.pollIntervalSeconds());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new MqRestTimeoutException(
            "Interrupted while polling for stop of " + name,
            name,
            "STOP",
            clock.elapsedSeconds(),
            e);
      }

      List<Map<String, Object>> statusRows;
      try {
        statusRows =
            mqscCommand("DISPLAY", config.statusQualifier(), name, null, List.of("all"), null);
      } catch (MqRestCommandException e) {
        statusRows = List.of();
      }
      polls++;

      if (config.emptyMeansStopped() && statusRows.isEmpty()) {
        return new SyncResult(SyncOperation.STOPPED, polls, clock.elapsedSeconds());
      }

      if (hasStatus(statusRows, config.statusKeys(), STOPPED_VALUES)) {
        return new SyncResult(SyncOperation.STOPPED, polls, clock.elapsedSeconds());
      }

      if (clock.elapsedSeconds() >= syncConfig.timeoutSeconds()) {
        throw new MqRestTimeoutException(
            "Timed out waiting for stop of " + name, name, "STOP", clock.elapsedSeconds());
      }
    }
  }

  private SyncResult restartObject(String name, ObjectTypeConfig config, SyncConfig syncConfig) {
    SyncResult stopResult = stopAndPoll(name, config, syncConfig);
    SyncResult startResult = startAndPoll(name, config, syncConfig);
    return new SyncResult(
        SyncOperation.RESTARTED,
        stopResult.polls() + startResult.polls(),
        stopResult.elapsedSeconds() + startResult.elapsedSeconds());
  }

  /**
   * Starts a channel and polls until it reaches RUNNING status.
   *
   * @param name the channel name
   * @param config polling configuration, or null for defaults
   * @return the sync result
   * @throws MqRestTimeoutException if the channel does not reach RUNNING in time
   */
  public SyncResult startChannelSync(String name, SyncConfig config) {
    return startAndPoll(name, CHANNEL_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Stops a channel and polls until it reaches STOPPED status or the status is empty.
   *
   * @param name the channel name
   * @param config polling configuration, or null for defaults
   * @return the sync result
   * @throws MqRestTimeoutException if the channel does not stop in time
   */
  public SyncResult stopChannelSync(String name, SyncConfig config) {
    return stopAndPoll(name, CHANNEL_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Restarts a channel (stop then start) with polling.
   *
   * @param name the channel name
   * @param config polling configuration, or null for defaults
   * @return the sync result with combined polls and elapsed time
   * @throws MqRestTimeoutException if either phase times out
   */
  public SyncResult restartChannel(String name, SyncConfig config) {
    return restartObject(name, CHANNEL_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Starts a listener and polls until it reaches RUNNING status.
   *
   * @param name the listener name
   * @param config polling configuration, or null for defaults
   * @return the sync result
   * @throws MqRestTimeoutException if the listener does not reach RUNNING in time
   */
  public SyncResult startListenerSync(String name, SyncConfig config) {
    return startAndPoll(name, LISTENER_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Stops a listener and polls until it reaches STOPPED status.
   *
   * @param name the listener name
   * @param config polling configuration, or null for defaults
   * @return the sync result
   * @throws MqRestTimeoutException if the listener does not stop in time
   */
  public SyncResult stopListenerSync(String name, SyncConfig config) {
    return stopAndPoll(name, LISTENER_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Restarts a listener (stop then start) with polling.
   *
   * @param name the listener name
   * @param config polling configuration, or null for defaults
   * @return the sync result with combined polls and elapsed time
   * @throws MqRestTimeoutException if either phase times out
   */
  public SyncResult restartListener(String name, SyncConfig config) {
    return restartObject(name, LISTENER_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Starts a service and polls until it reaches RUNNING status.
   *
   * @param name the service name
   * @param config polling configuration, or null for defaults
   * @return the sync result
   * @throws MqRestTimeoutException if the service does not reach RUNNING in time
   */
  public SyncResult startServiceSync(String name, SyncConfig config) {
    return startAndPoll(name, SERVICE_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Stops a service and polls until it reaches STOPPED status.
   *
   * @param name the service name
   * @param config polling configuration, or null for defaults
   * @return the sync result
   * @throws MqRestTimeoutException if the service does not stop in time
   */
  public SyncResult stopServiceSync(String name, SyncConfig config) {
    return stopAndPoll(name, SERVICE_CONFIG, config != null ? config : new SyncConfig());
  }

  /**
   * Restarts a service (stop then start) with polling.
   *
   * @param name the service name
   * @param config polling configuration, or null for defaults
   * @return the sync result with combined polls and elapsed time
   * @throws MqRestTimeoutException if either phase times out
   */
  public SyncResult restartService(String name, SyncConfig config) {
    return restartObject(name, SERVICE_CONFIG, config != null ? config : new SyncConfig());
  }

  /** Builder for {@link MqRestSession}. */
  public static final class Builder {

    private final String restBaseUrl;
    private final String qmgrName;
    private final Credentials credentials;
    private @Nullable MqRestTransport transport;
    private @Nullable String gatewayQmgr;
    private boolean verifyTls = true;
    private @Nullable Duration timeout = DEFAULT_TIMEOUT;
    private boolean mapAttributes = true;
    private boolean mappingStrict = true;
    private @Nullable Map<String, Object> mappingOverrides;
    private MappingOverrideMode mappingOverridesMode = MappingOverrideMode.MERGE;
    private @Nullable String csrfToken = DEFAULT_CSRF_TOKEN;

    /**
     * Creates a builder with the required session parameters.
     *
     * @param restBaseUrl the base URL of the MQ REST API
     * @param qmgrName the queue manager name
     * @param credentials the authentication credentials
     */
    public Builder(String restBaseUrl, String qmgrName, Credentials credentials) {
      this.restBaseUrl = Objects.requireNonNull(restBaseUrl, "restBaseUrl");
      this.qmgrName = Objects.requireNonNull(qmgrName, "qmgrName");
      this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    /** Sets the transport implementation. Required before calling {@link #build()}. */
    public Builder transport(MqRestTransport transport) {
      this.transport = Objects.requireNonNull(transport, "transport");
      return this;
    }

    /** Sets the gateway queue manager name. */
    public Builder gatewayQmgr(@Nullable String gatewayQmgr) {
      this.gatewayQmgr = gatewayQmgr;
      return this;
    }

    /** Sets whether to verify TLS certificates. Defaults to {@code true}. */
    public Builder verifyTls(boolean verifyTls) {
      this.verifyTls = verifyTls;
      return this;
    }

    /** Sets the request timeout. Defaults to 30 seconds. Pass {@code null} for no timeout. */
    public Builder timeout(@Nullable Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Sets whether to enable attribute mapping. Defaults to {@code true}. */
    public Builder mapAttributes(boolean mapAttributes) {
      this.mapAttributes = mapAttributes;
      return this;
    }

    /** Sets whether attribute mapping uses strict mode. Defaults to {@code true}. */
    public Builder mappingStrict(boolean mappingStrict) {
      this.mappingStrict = mappingStrict;
      return this;
    }

    /** Sets mapping overrides to apply on top of the default mapping data. */
    public Builder mappingOverrides(@Nullable Map<String, Object> mappingOverrides) {
      this.mappingOverrides =
          mappingOverrides != null ? new LinkedHashMap<>(mappingOverrides) : null;
      return this;
    }

    /** Sets the override mode. Defaults to {@link MappingOverrideMode#MERGE}. */
    public Builder mappingOverridesMode(MappingOverrideMode mappingOverridesMode) {
      this.mappingOverridesMode =
          Objects.requireNonNull(mappingOverridesMode, "mappingOverridesMode");
      return this;
    }

    /** Sets the CSRF token. Defaults to {@code "local"}. Pass {@code null} to omit the header. */
    public Builder csrfToken(@Nullable String csrfToken) {
      this.csrfToken = csrfToken;
      return this;
    }

    /**
     * Builds the session.
     *
     * @return the configured session
     * @throws NullPointerException if transport has not been set
     */
    public MqRestSession build() {
      Objects.requireNonNull(transport, "transport");
      return new MqRestSession(this);
    }
  }
}
