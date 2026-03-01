package io.github.wphillipmoore.mq.rest.admin.examples;

import io.github.wphillipmoore.mq.rest.admin.HttpClientTransport;
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Queue depth monitor.
 *
 * <p>Displays local queues with their current depth, flags queues approaching capacity, and sorts
 * by depth percentage.
 *
 * <p>Set {@code DEPTH_THRESHOLD_PCT} to change the warning threshold (default 80).
 */
public final class QueueDepthMonitor {

  private static final Set<String> LOCAL_TYPES = Set.of("QLOCAL", "LOCAL");

  /** Depth information for a single queue. */
  public record QueueDepthInfo(
      String name,
      int currentDepth,
      int maxDepth,
      double depthPct,
      int openInput,
      int openOutput,
      boolean warning) {}

  /** Monitor queue depths for a queue manager. */
  public static List<QueueDepthInfo> monitorQueueDepths(
      MqRestSession session, double thresholdPct) {
    List<Map<String, Object>> queues = session.displayQueue("*", null, null, null);
    List<QueueDepthInfo> results = new ArrayList<>();

    for (Map<String, Object> queue : queues) {
      String qtype = String.valueOf(queue.getOrDefault("type", "")).strip().toUpperCase(Locale.ROOT);
      if (!LOCAL_TYPES.contains(qtype)) {
        continue;
      }

      int currentDepth = toInt(queue.get("current_queue_depth"));
      int maxDepth = toInt(queue.get("max_queue_depth"));
      int openInput = toInt(queue.get("open_input_count"));
      int openOutput = toInt(queue.get("open_output_count"));
      double depthPct = maxDepth > 0 ? ((double) currentDepth / maxDepth * 100.0) : 0.0;

      results.add(
          new QueueDepthInfo(
              String.valueOf(queue.getOrDefault("queue_name", "")).strip(),
              currentDepth,
              maxDepth,
              depthPct,
              openInput,
              openOutput,
              depthPct >= thresholdPct));
    }

    results.sort(Comparator.comparingDouble(QueueDepthInfo::depthPct).reversed());
    return results;
  }

  /** Run the queue depth monitor and print results. */
  public static List<QueueDepthInfo> run(MqRestSession session, double thresholdPct) {
    List<QueueDepthInfo> results = monitorQueueDepths(session, thresholdPct);

    System.out.printf(
        "%n%-40s %8s %8s %6s %4s %4s %s%n", "Queue", "Depth", "Max", "%", "In", "Out", "Status");
    System.out.println("-".repeat(90));

    for (QueueDepthInfo info : results) {
      String status = info.warning() ? "WARNING" : "OK";
      System.out.printf(
          "%-40s %8d %8d %5.1f%% %4d %4d %s%n",
          info.name(),
          info.currentDepth(),
          info.maxDepth(),
          info.depthPct(),
          info.openInput(),
          info.openOutput(),
          status);
    }

    long warningCount = results.stream().filter(QueueDepthInfo::warning).count();
    System.out.printf("%nTotal queues: %d, warnings: %d%n", results.size(), warningCount);

    return results;
  }

  static int toInt(@Nullable Object value) {
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
    double threshold = Double.parseDouble(env("DEPTH_THRESHOLD_PCT", "80"));
    HttpClientTransport transport = new HttpClientTransport();

    MqRestSession session =
        new MqRestSession.Builder(
                env("MQ_REST_BASE_URL", "https://localhost:9453/ibmmq/rest/v2"),
                env("MQ_QMGR_NAME", "QM1"),
                new BasicAuth(env("MQ_ADMIN_USER", "mqadmin"), env("MQ_ADMIN_PASSWORD", "mqadmin")))
            .transport(transport)
            .verifyTls(false)
            .build();

    run(session, threshold);
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return value != null ? value : defaultValue;
  }

  private QueueDepthMonitor() {}
}
