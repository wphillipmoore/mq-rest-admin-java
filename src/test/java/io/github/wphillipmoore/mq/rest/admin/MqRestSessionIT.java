package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.auth.LtpaAuth;
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureAction;
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureResult;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestCommandException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests that exercise the MQ REST API against live queue managers.
 *
 * <p>Gated by the {@code MQ_REST_ADMIN_RUN_INTEGRATION} environment variable. Start the MQ
 * environment with {@code scripts/dev/mq_start.sh && scripts/dev/mq_seed.sh} before running.
 */
@EnabledIfEnvironmentVariable(named = "MQ_REST_ADMIN_RUN_INTEGRATION", matches = ".+")
class MqRestSessionIT {

  static final String REST_BASE_URL =
      System.getenv().getOrDefault("MQ_REST_BASE_URL", "https://localhost:9453/ibmmq/rest/v2");
  static final String QM1_NAME = System.getenv().getOrDefault("MQ_QMGR_NAME", "QM1");
  static final String QM2_REST_BASE_URL =
      System.getenv().getOrDefault("MQ_REST_BASE_URL_QM2", "https://localhost:9454/ibmmq/rest/v2");
  static final String QM2_NAME = System.getenv().getOrDefault("MQ_QMGR_NAME_QM2", "QM2");
  static final String ADMIN_USER = System.getenv().getOrDefault("MQ_ADMIN_USER", "mqadmin");
  static final String ADMIN_PASSWORD = System.getenv().getOrDefault("MQ_ADMIN_PASSWORD", "mqadmin");

  static final String TEST_QLOCAL = "DEV.TEST.QLOCAL";
  static final String TEST_QREMOTE = "DEV.TEST.QREMOTE";
  static final String TEST_QALIAS = "DEV.TEST.QALIAS";
  static final String TEST_QMODEL = "DEV.TEST.QMODEL";
  static final String TEST_CHANNEL = "DEV.TEST.SVRCONN";
  static final String TEST_LISTENER = "DEV.TEST.LSTR";
  static final String TEST_PROCESS = "DEV.TEST.PROC";
  static final String TEST_TOPIC = "DEV.TEST.TOPIC";
  static final String TEST_NAMELIST = "DEV.TEST.NAMELIST";
  static final String TEST_ENSURE_QLOCAL = "DEV.ENSURE.QLOCAL";
  static final String TEST_ENSURE_CHANNEL = "DEV.ENSURE.CHL";

  private static final Path REPO_ROOT = Path.of(System.getProperty("user.dir")).toAbsolutePath();
  private static final Path MQ_START_SCRIPT = REPO_ROOT.resolve("scripts/dev/mq_start.sh");
  private static final Path MQ_SEED_SCRIPT = REPO_ROOT.resolve("scripts/dev/mq_seed.sh");
  private static final Path MQ_STOP_SCRIPT = REPO_ROOT.resolve("scripts/dev/mq_stop.sh");
  private static final long MQ_READY_TIMEOUT_MS = 90_000;
  private static final long MQ_READY_SLEEP_MS = 2_000;

  static MqRestSession session;
  static MqRestSession qm2Session;
  private static boolean lifecycleManaged;

  @BeforeAll
  static void setUp() throws Exception {
    boolean skipLifecycle =
        "1".equals(System.getenv("MQ_SKIP_LIFECYCLE"))
            || "true".equalsIgnoreCase(System.getenv("MQ_SKIP_LIFECYCLE"));
    if (!skipLifecycle) {
      runScript(MQ_START_SCRIPT);
      lifecycleManaged = true;
    }
    waitForRestReady(REST_BASE_URL);
    if (!skipLifecycle) {
      runScript(MQ_SEED_SCRIPT);
    }
    HttpClientTransport transport = new HttpClientTransport();
    session = buildSession(transport, REST_BASE_URL, QM1_NAME);
    qm2Session = buildSession(transport, QM2_REST_BASE_URL, QM2_NAME);
  }

  @AfterAll
  static void tearDown() {
    if (lifecycleManaged) {
      try {
        runScript(MQ_STOP_SCRIPT);
      } catch (Exception e) {
        System.err.println("mq_stop.sh failed (best-effort): " + e.getMessage());
      }
    }
  }

  static MqRestSession buildSession(MqRestTransport transport, String baseUrl, String qmgrName) {
    return new MqRestSession.Builder(baseUrl, qmgrName, new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
        .transport(transport)
        .verifyTls(false)
        .mapAttributes(true)
        .mappingStrict(true)
        .build();
  }

  static MqRestSession buildNonStrictSession() {
    return new MqRestSession.Builder(
            REST_BASE_URL, QM1_NAME, new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
        .transport(new HttpClientTransport())
        .verifyTls(false)
        .mapAttributes(true)
        .mappingStrict(false)
        .build();
  }

  static boolean containsStringValue(Map<String, Object> obj, String expected) {
    String normalized = expected.strip().toUpperCase();
    for (Object value : obj.values()) {
      if (value instanceof String str && str.strip().toUpperCase().equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  static boolean anyContainsValue(List<Map<String, Object>> results, String expected) {
    return results.stream().anyMatch(r -> containsStringValue(r, expected));
  }

  @SuppressWarnings("unchecked")
  static String getDescriptionCaseInsensitive(Map<String, Object> attrs) {
    for (Map.Entry<String, Object> entry : attrs.entrySet()) {
      if (entry.getKey().equalsIgnoreCase("description")
          || entry.getKey().equalsIgnoreCase("DESCR")) {
        return String.valueOf(entry.getValue());
      }
    }
    return null;
  }

  static Map<String, Object> findMatchingObject(
      List<Map<String, Object>> results, String expected) {
    return results.stream().filter(r -> containsStringValue(r, expected)).findFirst().orElse(null);
  }

  static void silentDelete(Runnable deleteAction) {
    try {
      deleteAction.run();
    } catch (MqRestCommandException ignored) {
      // Object may not exist; ignore.
    }
  }

  // -------------------------------------------------------------------------
  // Display tests — read-only, exercising seeded objects
  // -------------------------------------------------------------------------

  @Nested
  class DisplayTests {

    @Test
    void displayQmgrReturnsObject() {
      Map<String, Object> result = session.displayQmgr(null, null);

      assertThat(result).isNotNull();
      assertThat(containsStringValue(result, QM1_NAME)).isTrue();
    }

    @Test
    void displayQmstatusReturnsObject() {
      Map<String, Object> result = session.displayQmstatus(null, null);

      assertThat(result).isNotNull();
    }

    @Test
    void displayCmdservReturnsObject() {
      Map<String, Object> result = session.displayCmdserv(null, null);

      // May be null on some platforms; just verify no exception.
      assertThat(result == null || result instanceof Map).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "DEV.DEAD.LETTER",
          "DEV.QLOCAL",
          "DEV.QREMOTE",
          "DEV.QALIAS",
          "DEV.QMODEL",
          "DEV.XMITQ"
        })
    void displaySeededQueues(String queueName) {
      List<Map<String, Object>> results = session.displayQueue(queueName, null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, queueName)).isTrue();
    }

    @Test
    void displayQstatusReturnsResult() {
      List<Map<String, Object>> results = session.displayQstatus("DEV.QLOCAL", null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, "DEV.QLOCAL")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV.SVRCONN", "DEV.SDR", "DEV.RCVR"})
    void displaySeededChannels(String channelName) {
      List<Map<String, Object>> results = session.displayChannel(channelName, null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, channelName)).isTrue();
    }

    @Test
    void displaySeededListener() {
      List<Map<String, Object>> results = session.displayListener("DEV.LSTR", null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, "DEV.LSTR")).isTrue();
    }

    @Test
    void displaySeededTopic() {
      List<Map<String, Object>> results = session.displayTopic("DEV.TOPIC", null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, "DEV.TOPIC")).isTrue();
    }

    @Test
    void displaySeededNamelist() {
      List<Map<String, Object>> results = session.displayNamelist("DEV.NAMELIST", null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, "DEV.NAMELIST")).isTrue();
    }

    @Test
    void displaySeededProcess() {
      List<Map<String, Object>> results = session.displayProcess("DEV.PROC", null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, "DEV.PROC")).isTrue();
    }
  }

  // -------------------------------------------------------------------------
  // Lifecycle tests — define, display, alter, delete
  // -------------------------------------------------------------------------

  @Nested
  class LifecycleTests {

    record LifecycleCase(
        String label,
        String objectName,
        Runnable define,
        java.util.function.Supplier<List<Map<String, Object>>> display,
        Runnable alter,
        Runnable delete,
        String alterDescription) {}

    static Stream<LifecycleCase> lifecycleCases() {
      return Stream.of(
          new LifecycleCase(
              "qlocal",
              TEST_QLOCAL,
              () ->
                  session.defineQlocal(
                      TEST_QLOCAL,
                      Map.of(
                          "replace", "yes",
                          "default_persistence", "yes",
                          "description", "dev test qlocal"),
                      null),
              () -> session.displayQueue(TEST_QLOCAL, null, null, null),
              () -> {}, // no alter for qlocal in pymqrest reference
              () -> session.deleteQlocal(TEST_QLOCAL, null, null),
              null),
          new LifecycleCase(
              "qremote",
              TEST_QREMOTE,
              () ->
                  session.defineQremote(
                      TEST_QREMOTE,
                      Map.of(
                          "replace", "yes",
                          "remote_queue_name", "DEV.TARGET",
                          "remote_queue_manager_name", QM1_NAME,
                          "transmission_queue_name", "DEV.XMITQ",
                          "description", "dev test qremote"),
                      null),
              () -> session.displayQueue(TEST_QREMOTE, null, null, null),
              () -> {},
              () -> session.deleteQremote(TEST_QREMOTE, null, null),
              null),
          new LifecycleCase(
              "qalias",
              TEST_QALIAS,
              () ->
                  session.defineQalias(
                      TEST_QALIAS,
                      Map.of(
                          "replace", "yes",
                          "target_queue_name", "DEV.QLOCAL",
                          "description", "dev test qalias"),
                      null),
              () -> session.displayQueue(TEST_QALIAS, null, null, null),
              () -> {},
              () -> session.deleteQalias(TEST_QALIAS, null, null),
              null),
          new LifecycleCase(
              "qmodel",
              TEST_QMODEL,
              () ->
                  session.defineQmodel(
                      TEST_QMODEL,
                      Map.of(
                          "replace", "yes",
                          "definition_type", "TEMPDYN",
                          "default_input_open_option", "SHARED",
                          "description", "dev test qmodel"),
                      null),
              () -> session.displayQueue(TEST_QMODEL, null, null, null),
              () -> {},
              () -> session.deleteQmodel(TEST_QMODEL, null, null),
              null),
          new LifecycleCase(
              "channel",
              TEST_CHANNEL,
              () ->
                  session.defineChannel(
                      TEST_CHANNEL,
                      Map.of(
                          "replace", "yes",
                          "channel_type", "SVRCONN",
                          "transport_type", "TCP",
                          "description", "dev test channel"),
                      null),
              () -> session.displayChannel(TEST_CHANNEL, null, null, null),
              () ->
                  session.alterChannel(
                      TEST_CHANNEL,
                      Map.of(
                          "channel_type", "SVRCONN",
                          "description", "dev test channel updated"),
                      null),
              () -> session.deleteChannel(TEST_CHANNEL, null, null),
              "dev test channel updated"),
          new LifecycleCase(
              "listener",
              TEST_LISTENER,
              () ->
                  session.defineListener(
                      TEST_LISTENER,
                      Map.of(
                          "replace", "yes",
                          "transport_type", "TCP",
                          "port", 1416,
                          "start_mode", "QMGR",
                          "description", "dev test listener"),
                      null),
              () -> session.displayListener(TEST_LISTENER, null, null, null),
              () ->
                  session.alterListener(
                      TEST_LISTENER,
                      Map.of("transport_type", "TCP", "description", "dev test listener updated"),
                      null),
              () -> session.deleteListener(TEST_LISTENER, null, null),
              "dev test listener updated"),
          new LifecycleCase(
              "process",
              TEST_PROCESS,
              () ->
                  session.defineProcess(
                      TEST_PROCESS,
                      Map.of(
                          "replace", "yes",
                          "application_id", "/bin/true",
                          "description", "dev test process"),
                      null),
              () -> session.displayProcess(TEST_PROCESS, null, null, null),
              () ->
                  session.alterProcess(
                      TEST_PROCESS, Map.of("description", "dev test process updated"), null),
              () -> session.deleteProcess(TEST_PROCESS, null, null),
              "dev test process updated"),
          new LifecycleCase(
              "topic",
              TEST_TOPIC,
              () ->
                  session.defineTopic(
                      TEST_TOPIC,
                      Map.of(
                          "replace", "yes",
                          "topic_string", "dev/test",
                          "description", "dev test topic"),
                      null),
              () -> session.displayTopic(TEST_TOPIC, null, null, null),
              () ->
                  session.alterTopic(
                      TEST_TOPIC, Map.of("description", "dev test topic updated"), null),
              () -> session.deleteTopic(TEST_TOPIC, null, null),
              "dev test topic updated"),
          new LifecycleCase(
              "namelist",
              TEST_NAMELIST,
              () ->
                  session.defineNamelist(
                      TEST_NAMELIST,
                      Map.of(
                          "replace", "yes",
                          "names", List.of("DEV.QLOCAL"),
                          "description", "dev test namelist"),
                      null),
              () -> session.displayNamelist(TEST_NAMELIST, null, null, null),
              () ->
                  session.alterNamelist(
                      TEST_NAMELIST, Map.of("description", "dev test namelist updated"), null),
              () -> session.deleteNamelist(TEST_NAMELIST, null, null),
              "dev test namelist updated"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lifecycleCases")
    void mutatingObjectLifecycle(LifecycleCase tc) {
      try {
        // Define
        tc.define().run();

        // Display and verify
        List<Map<String, Object>> displayed = tc.display().get();
        assertThat(displayed).isNotEmpty();
        assertThat(anyContainsValue(displayed, tc.objectName())).isTrue();

        // Alter and verify (if applicable)
        if (tc.alterDescription() != null) {
          tc.alter().run();
          List<Map<String, Object>> updated = tc.display().get();
          Map<String, Object> matched = findMatchingObject(updated, tc.objectName());
          assertThat(matched).isNotNull();
          assertThat(getDescriptionCaseInsensitive(matched)).isEqualTo(tc.alterDescription());
        }
      } finally {
        // Always clean up
        silentDelete(tc.delete());
      }

      // Verify deletion
      try {
        List<Map<String, Object>> afterDelete = tc.display().get();
        assertThat(anyContainsValue(afterDelete, tc.objectName())).isFalse();
      } catch (MqRestCommandException ignored) {
        // Object not found is the expected outcome.
      }
    }
  }

  // -------------------------------------------------------------------------
  // Ensure tests — idempotent create/update
  // -------------------------------------------------------------------------

  @Nested
  class EnsureTests {

    @Test
    void ensureQmgrLifecycle() {
      // Read current description so we can restore it.
      Map<String, Object> qmgr = session.displayQmgr(null, null);
      assertThat(qmgr).isNotNull();
      String originalDescr = String.valueOf(qmgr.getOrDefault("description", ""));

      String testDescr = "dev ensure_qmgr test";

      try {
        // Set to test value.
        EnsureResult result = session.ensureQmgr(Map.of("description", testDescr));
        assertThat(result.action()).isIn(EnsureAction.UPDATED, EnsureAction.UNCHANGED);

        // Same attributes → UNCHANGED.
        result = session.ensureQmgr(Map.of("description", testDescr));
        assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
      } finally {
        // Restore original description.
        session.ensureQmgr(Map.of("description", originalDescr));
      }
    }

    @Test
    void ensureQlocalLifecycle() {
      MqRestSession nonStrict = buildNonStrictSession();

      // Clean up from any prior failed run.
      silentDelete(() -> nonStrict.deleteQlocal(TEST_ENSURE_QLOCAL, null, null));

      try {
        // Create.
        EnsureResult result =
            nonStrict.ensureQlocal(TEST_ENSURE_QLOCAL, Map.of("description", "ensure test"));
        assertThat(result.action()).isEqualTo(EnsureAction.CREATED);

        // Same attributes → UNCHANGED.
        result = nonStrict.ensureQlocal(TEST_ENSURE_QLOCAL, Map.of("description", "ensure test"));
        assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);

        // Different attribute → UPDATED.
        result =
            nonStrict.ensureQlocal(TEST_ENSURE_QLOCAL, Map.of("description", "ensure updated"));
        assertThat(result.action()).isEqualTo(EnsureAction.UPDATED);
      } finally {
        silentDelete(() -> nonStrict.deleteQlocal(TEST_ENSURE_QLOCAL, null, null));
      }
    }

    @Test
    void ensureChannelLifecycle() {
      MqRestSession nonStrict = buildNonStrictSession();

      // Clean up from any prior failed run.
      silentDelete(() -> nonStrict.deleteChannel(TEST_ENSURE_CHANNEL, null, null));

      try {
        // Create.
        EnsureResult result =
            nonStrict.ensureChannel(
                TEST_ENSURE_CHANNEL,
                Map.of("channel_type", "SVRCONN", "description", "ensure test"));
        assertThat(result.action()).isEqualTo(EnsureAction.CREATED);

        // Same attributes → UNCHANGED.
        result =
            nonStrict.ensureChannel(
                TEST_ENSURE_CHANNEL,
                Map.of("channel_type", "SVRCONN", "description", "ensure test"));
        assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);

        // Different attribute → UPDATED.
        result =
            nonStrict.ensureChannel(
                TEST_ENSURE_CHANNEL,
                Map.of("channel_type", "SVRCONN", "description", "ensure updated"));
        assertThat(result.action()).isEqualTo(EnsureAction.UPDATED);
      } finally {
        silentDelete(() -> nonStrict.deleteChannel(TEST_ENSURE_CHANNEL, null, null));
      }
    }
  }

  // -------------------------------------------------------------------------
  // Gateway tests — multi-QM access
  // -------------------------------------------------------------------------

  @Nested
  class GatewayTests {

    @Test
    void displayQmgrQm2ViaQm1() {
      MqRestSession gateway =
          new MqRestSession.Builder(
                  REST_BASE_URL, QM2_NAME, new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
              .transport(new HttpClientTransport())
              .verifyTls(false)
              .gatewayQmgr(QM1_NAME)
              .build();

      Map<String, Object> result = gateway.displayQmgr(null, null);

      assertThat(result).isNotNull();
      assertThat(containsStringValue(result, QM2_NAME)).isTrue();
    }

    @Test
    void displayQmgrQm1ViaQm2() {
      MqRestSession gateway =
          new MqRestSession.Builder(
                  QM2_REST_BASE_URL, QM1_NAME, new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
              .transport(new HttpClientTransport())
              .verifyTls(false)
              .gatewayQmgr(QM2_NAME)
              .build();

      Map<String, Object> result = gateway.displayQmgr(null, null);

      assertThat(result).isNotNull();
      assertThat(containsStringValue(result, QM1_NAME)).isTrue();
    }

    @Test
    void displayQueueQm2ViaQm1() {
      MqRestSession gateway =
          new MqRestSession.Builder(
                  REST_BASE_URL, QM2_NAME, new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
              .transport(new HttpClientTransport())
              .verifyTls(false)
              .gatewayQmgr(QM1_NAME)
              .build();

      List<Map<String, Object>> results = gateway.displayQueue("DEV.QLOCAL", null, null, null);

      assertThat(results).isNotEmpty();
      assertThat(anyContainsValue(results, "DEV.QLOCAL")).isTrue();
    }

    @Test
    void gatewaySessionProperties() {
      MqRestSession gateway =
          new MqRestSession.Builder(
                  REST_BASE_URL, QM2_NAME, new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
              .transport(new HttpClientTransport())
              .verifyTls(false)
              .gatewayQmgr(QM1_NAME)
              .build();

      assertThat(gateway.getQmgrName()).isEqualTo(QM2_NAME);
      assertThat(gateway.getGatewayQmgr()).isEqualTo(QM1_NAME);
    }
  }

  // -------------------------------------------------------------------------
  // Session state tests — verify state accessors after a command
  // -------------------------------------------------------------------------

  @Nested
  class SessionStateTests {

    @Test
    void sessionStatePopulatedAfterCommand() {
      session.displayQmgr(null, null);

      assertThat(session.getLastHttpStatus()).isNotNull();
      assertThat(session.getLastResponseText()).isNotNull();
      assertThat(session.getLastResponsePayload()).isNotNull();
    }
  }

  // -------------------------------------------------------------------------
  // LTPA auth tests
  // -------------------------------------------------------------------------

  @Nested
  class LtpaTests {

    @Test
    void ltpaAuthDisplayQmgr() {
      MqRestSession ltpaSession =
          new MqRestSession.Builder(
                  REST_BASE_URL, QM1_NAME, new LtpaAuth(ADMIN_USER, ADMIN_PASSWORD))
              .transport(new HttpClientTransport())
              .verifyTls(false)
              .build();

      Map<String, Object> result = ltpaSession.displayQmgr(null, null);

      assertThat(result).isNotNull();
      assertThat(containsStringValue(result, QM1_NAME)).isTrue();
    }
  }

  // -------------------------------------------------------------------------
  // Lifecycle helpers
  // -------------------------------------------------------------------------

  private static void runScript(Path script) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder("bash", script.toString());
    pb.inheritIO();
    pb.directory(REPO_ROOT.toFile());
    int exitCode = pb.start().waitFor();
    if (exitCode != 0) {
      throw new IOException("Script " + script.getFileName() + " exited with code " + exitCode);
    }
  }

  private static void waitForRestReady(String baseUrl)
      throws NoSuchAlgorithmException, KeyManagementException, InterruptedException {
    TrustManager[] trustAll = {
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }
    };
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAll, new SecureRandom());

    HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/admin/qmgr"))
            .header(
                "Authorization",
                "Basic "
                    + java.util.Base64.getEncoder()
                        .encodeToString((ADMIN_USER + ":" + ADMIN_PASSWORD).getBytes()))
            .header("ibm-mq-rest-csrf-token", "blank")
            .GET()
            .build();

    long deadline = System.currentTimeMillis() + MQ_READY_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          return;
        }
      } catch (IOException ignored) {
        // REST endpoint not ready yet
      }
      Thread.sleep(MQ_READY_SLEEP_MS);
    }
    throw new RuntimeException(
        "MQ REST endpoint not ready after " + (MQ_READY_TIMEOUT_MS / 1000) + "s");
  }
}
