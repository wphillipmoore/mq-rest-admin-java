package io.github.wphillipmoore.mq.rest.admin.examples;

import io.github.wphillipmoore.mq.rest.admin.HttpClientTransport;
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import java.util.List;
import java.util.Map;

/**
 * Dead letter queue inspector.
 *
 * <p>Checks the dead letter queue configuration for a queue manager, reports its depth and
 * capacity, and suggests actions when messages are present.
 */
public final class DlqInspector {

  static final double CRITICAL_DEPTH_PCT = 90.0;

  /** Dead letter queue inspection result. */
  public record DLQReport(
      String qmgrName,
      String dlqName,
      boolean configured,
      int currentDepth,
      int maxDepth,
      double depthPct,
      int openInput,
      int openOutput,
      String suggestion) {}

  /** Inspect the dead letter queue for a queue manager. */
  public static DLQReport inspectDlq(MqRestSession session) {
    Map<String, Object> qmgr = session.displayQmgr(null, null);

    String dlqName = "";
    if (qmgr != null) {
      Object raw = qmgr.get("dead_letter_queue_name");
      if (raw != null) {
        dlqName = raw.toString().strip();
      }
    }

    if (dlqName.isEmpty()) {
      return new DLQReport(
          session.getQmgrName(),
          "",
          false,
          0,
          0,
          0.0,
          0,
          0,
          "No dead letter queue configured. Define one with ALTER QMGR DEADQ.");
    }

    List<Map<String, Object>> queues = session.displayQueue(dlqName, null, null, null);
    if (queues.isEmpty()) {
      return new DLQReport(
          session.getQmgrName(),
          dlqName,
          true,
          0,
          0,
          0.0,
          0,
          0,
          "DLQ '" + dlqName + "' is configured but the queue does not exist.");
    }

    Map<String, Object> dlq = queues.get(0);
    int currentDepth = toInt(dlq.get("current_queue_depth"));
    int maxDepth = toInt(dlq.get("max_queue_depth"));
    int openInput = toInt(dlq.get("open_input_count"));
    int openOutput = toInt(dlq.get("open_output_count"));
    double depthPct = maxDepth > 0 ? ((double) currentDepth / maxDepth * 100.0) : 0.0;

    String suggestion;
    if (currentDepth == 0) {
      suggestion = "DLQ is empty. No action needed.";
    } else if (depthPct >= CRITICAL_DEPTH_PCT) {
      suggestion = "DLQ is near capacity. Investigate and clear undeliverable messages urgently.";
    } else {
      suggestion = "DLQ has messages. Investigate undeliverable messages.";
    }

    return new DLQReport(
        session.getQmgrName(),
        dlqName,
        true,
        currentDepth,
        maxDepth,
        depthPct,
        openInput,
        openOutput,
        suggestion);
  }

  /** Run the DLQ inspection and print results. */
  public static DLQReport run(MqRestSession session) {
    DLQReport report = inspectDlq(session);

    System.out.printf("%n=== Dead Letter Queue: %s ===%n", report.qmgrName());
    System.out.printf("  Configured: %s%n", report.configured());
    System.out.printf(
        "  DLQ name:   %s%n", report.dlqName().isEmpty() ? "(none)" : report.dlqName());

    if (report.configured() && !report.dlqName().isEmpty()) {
      System.out.printf(
          "  Depth:      %d / %d (%.1f%%)%n",
          report.currentDepth(), report.maxDepth(), report.depthPct());
      System.out.printf("  Input:      %d%n", report.openInput());
      System.out.printf("  Output:     %d%n", report.openOutput());
    }

    System.out.printf("  Suggestion: %s%n", report.suggestion());
    return report;
  }

  private static int toInt(Object value) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    if (value != null) {
      try {
        return Integer.parseInt(value.toString().strip());
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
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

  private DlqInspector() {}
}
