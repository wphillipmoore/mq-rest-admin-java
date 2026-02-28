package io.github.wphillipmoore.mq.rest.admin.examples;

import io.github.wphillipmoore.mq.rest.admin.HttpClientTransport;
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Queue manager health check.
 *
 * <p>Connects to one or more queue managers and checks QMGR status, command server availability,
 * and listener state. Produces a pass/fail summary for each queue manager.
 *
 * <p>Usage: {@code java -cp ... io.github.wphillipmoore.mq.rest.admin.examples.HealthCheck}
 *
 * <p>Set {@code MQ_REST_BASE_URL_QM2} to also check QM2.
 */
public final class HealthCheck {

  /** Health status for a single listener. */
  public record ListenerResult(String name, String status) {}

  /** Health check result for a single queue manager. */
  public record QMHealthResult(
      String qmgrName,
      boolean reachable,
      String status,
      String commandServer,
      List<ListenerResult> listeners,
      boolean passed) {}

  /** Run a health check against a single queue manager. */
  public static QMHealthResult checkHealth(MqRestSession session) {
    Map<String, Object> qmgr;
    try {
      qmgr = session.displayQmgr(null, null);
    } catch (MqRestException e) {
      return new QMHealthResult(
          session.getQmgrName(), false, "UNKNOWN", "UNKNOWN", List.of(), false);
    }

    String qmgrName = session.getQmgrName();
    if (qmgr != null) {
      Object name = qmgr.get("queue_manager_name");
      if (name instanceof String s && !s.strip().isEmpty()) {
        qmgrName = s.strip();
      }
    }

    String status = extractStatus(session);
    String commandServer = extractCommandServer(session);
    List<ListenerResult> listeners = fetchListeners(session);

    boolean passed = !"UNKNOWN".equals(status);
    return new QMHealthResult(qmgrName, true, status, commandServer, listeners, passed);
  }

  /** Run health checks against one or more queue managers and print results. */
  public static List<QMHealthResult> main(List<MqRestSession> sessions) {
    List<QMHealthResult> results = new ArrayList<>();
    for (MqRestSession session : sessions) {
      QMHealthResult result = checkHealth(session);
      results.add(result);
      printResult(result);
    }
    return results;
  }

  private static String extractStatus(MqRestSession session) {
    Map<String, Object> qmstatus = session.displayQmstatus(null, null);
    if (qmstatus != null) {
      Object ha = qmstatus.get("ha_status");
      if (ha != null) {
        return ha.toString().strip();
      }
    }
    return "UNKNOWN";
  }

  private static String extractCommandServer(MqRestSession session) {
    Map<String, Object> cmdserv = session.displayCmdserv(null, null);
    if (cmdserv != null) {
      Object cs = cmdserv.get("status");
      if (cs != null) {
        return cs.toString().strip();
      }
    }
    return "UNKNOWN";
  }

  private static List<ListenerResult> fetchListeners(MqRestSession session) {
    try {
      List<Map<String, Object>> raw = session.displayListener("*", null, null, null);
      List<ListenerResult> listeners = new ArrayList<>();
      for (Map<String, Object> listener : raw) {
        String lname = String.valueOf(listener.getOrDefault("listener_name", "")).strip();
        String lstatus = String.valueOf(listener.getOrDefault("start_mode", "")).strip();
        listeners.add(new ListenerResult(lname, lstatus));
      }
      return Collections.unmodifiableList(listeners);
    } catch (MqRestException e) {
      return List.of();
    }
  }

  private static void printResult(QMHealthResult result) {
    String verdict = result.passed() ? "PASS" : "FAIL";
    System.out.printf("%n=== %s: %s ===%n", result.qmgrName(), verdict);
    System.out.printf("  Reachable:      %s%n", result.reachable());
    System.out.printf("  Status:         %s%n", result.status());
    System.out.printf("  Command server: %s%n", result.commandServer());
    System.out.printf("  Listeners:      %d%n", result.listeners().size());
    for (ListenerResult listener : result.listeners()) {
      System.out.printf("    %s: %s%n", listener.name(), listener.status());
    }
  }

  /** Entry point. */
  public static void main(String[] args) {
    List<MqRestSession> sessions = new ArrayList<>();
    HttpClientTransport transport = new HttpClientTransport();

    sessions.add(
        new MqRestSession.Builder(
                env("MQ_REST_BASE_URL", "https://localhost:9453/ibmmq/rest/v2"),
                env("MQ_QMGR_NAME", "QM1"),
                new BasicAuth(env("MQ_ADMIN_USER", "mqadmin"), env("MQ_ADMIN_PASSWORD", "mqadmin")))
            .transport(transport)
            .verifyTls(false)
            .build());

    String qm2Url = System.getenv("MQ_REST_BASE_URL_QM2");
    if (qm2Url != null) {
      sessions.add(
          new MqRestSession.Builder(
                  qm2Url,
                  "QM2",
                  new BasicAuth(
                      env("MQ_ADMIN_USER", "mqadmin"), env("MQ_ADMIN_PASSWORD", "mqadmin")))
              .transport(transport)
              .verifyTls(false)
              .build());
    }

    main(sessions);
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return value != null ? value : defaultValue;
  }

  private HealthCheck() {}
}
