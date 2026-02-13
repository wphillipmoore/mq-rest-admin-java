package io.github.wphillipmoore.mq.rest.admin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.auth.Credentials;
import io.github.wphillipmoore.mq.rest.admin.auth.LtpaAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestAuthException;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestCommandException;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestResponseException;
import io.github.wphillipmoore.mq.rest.admin.mapping.AttributeMapper;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingData;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingException;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingIssue;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingOverrideMode;
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
import java.util.TreeMap;

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

  private final String restBaseUrl;
  private final String qmgrName;
  private final Credentials credentials;
  private final MqRestTransport transport;
  private final String gatewayQmgr;
  private final boolean verifyTls;
  private final Duration timeout;
  private final boolean mapAttributes;
  private final boolean mappingStrict;
  private final String csrfToken;
  private final MappingData mappingData;
  private final AttributeMapper attributeMapper;

  private String ltpaToken;
  private Integer lastHttpStatus;
  private String lastResponseText;
  private Map<String, Object> lastResponsePayload;
  private Map<String, Object> lastCommandPayload;

  private MqRestSession(Builder builder) {
    this.restBaseUrl = stripTrailingSlashes(builder.restBaseUrl);
    this.qmgrName = builder.qmgrName;
    this.credentials = builder.credentials;
    this.transport = builder.transport;
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
  public String getGatewayQmgr() {
    return gatewayQmgr;
  }

  /** Returns the HTTP status code of the last command, or {@code null} before any command. */
  public Integer getLastHttpStatus() {
    return lastHttpStatus;
  }

  /** Returns the raw response text of the last command, or {@code null} before any command. */
  public String getLastResponseText() {
    return lastResponseText;
  }

  /**
   * Returns the parsed response payload of the last command, or {@code null} before any command.
   * The returned map is unmodifiable.
   */
  public Map<String, Object> getLastResponsePayload() {
    return lastResponsePayload;
  }

  /**
   * Returns the command payload sent in the last command, or {@code null} before any command. The
   * returned map is unmodifiable.
   */
  public Map<String, Object> getLastCommandPayload() {
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
      String name,
      Map<String, Object> requestParameters,
      List<String> responseParameters,
      String where) {

    // 1. Normalize command/qualifier to uppercase
    String upperCommand = command.toUpperCase(Locale.ROOT);
    String upperQualifier = mqscQualifier.toUpperCase(Locale.ROOT);

    // 2. Copy requestParameters to mutable map
    Map<String, Object> params =
        requestParameters != null ? new LinkedHashMap<>(requestParameters) : new LinkedHashMap<>();

    // 3. Normalize responseParameters
    boolean isDisplay = "DISPLAY".equals(upperCommand);
    List<String> respParams = normalizeResponseParameters(responseParameters, isDisplay);

    // 4. Resolve mapping qualifier
    String mappingQualifier = resolveMappingQualifier(upperCommand, upperQualifier);

    // 5. Map request attributes if enabled
    if (mapAttributes) {
      params = attributeMapper.mapRequestAttributes(mappingQualifier, params, mappingStrict);
      respParams =
          mapResponseParameters(upperCommand, upperQualifier, mappingQualifier, respParams);
    }

    // 6. Map WHERE keyword if provided
    if (where != null && !where.isBlank() && mapAttributes) {
      where = mapWhereKeyword(where, mappingQualifier);
    }
    if (where != null && !where.isBlank()) {
      params.put("WHERE", where);
    }

    // 7. Build command payload
    Map<String, Object> payload =
        buildCommandPayload(upperCommand, upperQualifier, name, params, respParams);
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

    // 13. Flatten nested objects
    commandResponse = flattenNestedObjects(commandResponse);

    // 14. Map response attributes if enabled
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
      headers.put("Cookie", LTPA_COOKIE_NAME + "=" + ltpaToken);
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

    String token = extractLtpaToken(response.headers());
    if (token == null) {
      throw new MqRestAuthException(
          "LTPA login succeeded but LtpaToken2 cookie not found in response",
          loginUrl,
          response.statusCode());
    }
    this.ltpaToken = token;
  }

  static String extractLtpaToken(Map<String, String> headers) {
    // Look for Set-Cookie header (case-insensitive)
    String setCookie = null;
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if ("set-cookie".equalsIgnoreCase(entry.getKey())) {
        setCookie = entry.getValue();
        break;
      }
    }
    if (setCookie == null) {
      return null;
    }
    // Parse cookie string for LtpaToken2
    for (String part : setCookie.split(";")) {
      String trimmed = part.trim();
      if (trimmed.startsWith(LTPA_COOKIE_NAME + "=")) {
        return trimmed.substring(LTPA_COOKIE_NAME.length() + 1);
      }
    }
    return null;
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
      String name,
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

  static List<String> normalizeResponseParameters(List<String> params, boolean isDisplay) {
    if (params == null) {
      return isDisplay ? List.of("all") : List.of();
    }
    // Check for "all" (case-insensitive)
    for (String param : params) {
      if ("all".equalsIgnoreCase(param)) {
        return List.of("all");
      }
    }
    return new ArrayList<>(params);
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
      StringBuilder msg = new StringBuilder("MQSC command error");
      if (overallCompletionCode != null || overallReasonCode != null) {
        msg.append(" (overallCompletionCode=")
            .append(overallCompletionCode)
            .append(", overallReasonCode=")
            .append(overallReasonCode)
            .append(")");
      }
      throw new MqRestCommandException(msg.toString(), payload, statusCode);
    }
  }

  private static boolean hasErrorCodes(Integer completionCode, Integer reasonCode) {
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

  static Map<String, Object> normalizeResponseAttributes(Map<String, Object> attrs) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : attrs.entrySet()) {
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
    String keyword;
    String rest;
    int spaceIdx = indexOfFirstWhitespace(where);
    if (spaceIdx < 0) {
      keyword = where;
      rest = null;
    } else {
      keyword = where.substring(0, spaceIdx);
      rest = where.substring(spaceIdx + 1);
    }

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

    if (rest != null) {
      return mappedKeyword + " " + rest;
    }
    return mappedKeyword;
  }

  private static int indexOfFirstWhitespace(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  static Integer extractOptionalInt(Object value) {
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

  /** Builder for {@link MqRestSession}. */
  public static final class Builder {

    private final String restBaseUrl;
    private final String qmgrName;
    private final Credentials credentials;
    private MqRestTransport transport;
    private String gatewayQmgr;
    private boolean verifyTls = true;
    private Duration timeout = DEFAULT_TIMEOUT;
    private boolean mapAttributes = true;
    private boolean mappingStrict = true;
    private Map<String, Object> mappingOverrides;
    private MappingOverrideMode mappingOverridesMode = MappingOverrideMode.MERGE;
    private String csrfToken = DEFAULT_CSRF_TOKEN;

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
    public Builder gatewayQmgr(String gatewayQmgr) {
      this.gatewayQmgr = gatewayQmgr;
      return this;
    }

    /** Sets whether to verify TLS certificates. Defaults to {@code true}. */
    public Builder verifyTls(boolean verifyTls) {
      this.verifyTls = verifyTls;
      return this;
    }

    /** Sets the request timeout. Defaults to 30 seconds. Pass {@code null} for no timeout. */
    public Builder timeout(Duration timeout) {
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
    public Builder mappingOverrides(Map<String, Object> mappingOverrides) {
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
    public Builder csrfToken(String csrfToken) {
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
