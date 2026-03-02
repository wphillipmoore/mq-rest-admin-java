package io.github.wphillipmoore.mq.rest.admin.examples;

import io.github.wphillipmoore.mq.rest.admin.HttpClientTransport;
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestException;
import java.util.List;
import java.util.Map;

/**
 * Queue status and connection handle report.
 *
 * <p>Demonstrates {@code DISPLAY QSTATUS TYPE(HANDLE)} and {@code DISPLAY CONN TYPE(HANDLE)}
 * queries, showing how mq-rest-admin transparently flattens the nested objects response structure
 * into uniform flat maps.
 */
public final class QueueStatus {

  /** Per-handle queue status information. */
  public record QueueHandleInfo(
      String queueName, String handleState, String connectionId, String openOptions) {}

  /** Per-handle connection information. */
  public record ConnectionHandleInfo(
      String connectionId, String objectName, String handleState, String objectType) {}

  /** Report per-handle queue status entries. */
  public static List<QueueHandleInfo> reportQueueHandles(MqRestSession session) {
    try {
      List<Map<String, Object>> entries =
          session.displayQstatus("*", Map.of("type", "HANDLE"), null, null);
      return entries.stream()
          .map(
              entry ->
                  new QueueHandleInfo(
                      str(entry, "queue_name"),
                      str(entry, "handle_state"),
                      str(entry, "connection_id"),
                      str(entry, "open_options")))
          .toList();
    } catch (MqRestException e) {
      return List.of();
    }
  }

  /** Report per-handle connection entries. */
  public static List<ConnectionHandleInfo> reportConnectionHandles(MqRestSession session) {
    try {
      List<Map<String, Object>> entries =
          session.displayConn("*", Map.of("connection_info_type", "HANDLE"), null, null);
      return entries.stream()
          .map(
              entry ->
                  new ConnectionHandleInfo(
                      str(entry, "connection_id"),
                      str(entry, "object_name"),
                      str(entry, "handle_state"),
                      str(entry, "object_type")))
          .toList();
    } catch (MqRestException e) {
      return List.of();
    }
  }

  /** Run the queue status and connection handle report. */
  public static void run(MqRestSession session) {
    List<QueueHandleInfo> queueHandles = reportQueueHandles(session);

    System.out.printf(
        "%n%-30s %-15s %-30s %s%n", "Queue", "Handle State", "Connection ID", "Open Options");
    System.out.println("-".repeat(90));
    for (QueueHandleInfo info : queueHandles) {
      System.out.printf(
          "%-30s %-15s %-30s %s%n",
          info.queueName(), info.handleState(), info.connectionId(), info.openOptions());
    }
    if (queueHandles.isEmpty()) {
      System.out.println("  (no active queue handles)");
    }

    List<ConnectionHandleInfo> connHandles = reportConnectionHandles(session);

    System.out.printf(
        "%n%-30s %-30s %-15s %s%n", "Connection ID", "Object Name", "Handle State", "Object Type");
    System.out.println("-".repeat(90));
    for (ConnectionHandleInfo info : connHandles) {
      System.out.printf(
          "%-30s %-30s %-15s %s%n",
          info.connectionId(), info.objectName(), info.handleState(), info.objectType());
    }
    if (connHandles.isEmpty()) {
      System.out.println("  (no active connection handles)");
    }
  }

  private static String str(Map<String, Object> entry, String key) {
    return String.valueOf(entry.getOrDefault(key, "")).strip();
  }

  /** Entry point. */
  public static void main(String[] args) {
    HttpClientTransport transport = new HttpClientTransport();

    MqRestSession session =
        new MqRestSession.Builder(
                env("MQ_REST_BASE_URL", "https://localhost:9453/ibmmq/rest/v2"),
                env("MQ_QMGR_NAME", "QM1"),
                new BasicAuth(env("MQ_ADMIN_USER", "mqadmin"), env("MQ_ADMIN_PASSWORD", "mqadmin")))
            .transport(transport)
            .verifyTls(false)
            .build();

    run(session);
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return value != null ? value : defaultValue;
  }

  private QueueStatus() {}
}
