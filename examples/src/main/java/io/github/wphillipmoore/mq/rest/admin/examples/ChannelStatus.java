package io.github.wphillipmoore.mq.rest.admin.examples;

import io.github.wphillipmoore.mq.rest.admin.HttpClientTransport;
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Channel status report.
 *
 * <p>Displays channel definitions alongside live channel status, identifies channels that are
 * defined but not running, and shows connection details.
 */
public final class ChannelStatus {

  /** Combined channel definition and status information. */
  public record ChannelInfo(
      String name, String channelType, String connectionName, boolean defined, String status) {}

  /** Report channel definitions and live status. */
  public static List<ChannelInfo> reportChannelStatus(MqRestSession session) {
    Map<String, Map<String, Object>> definitions = fetchDefinitions(session);
    Map<String, String> liveStatus = fetchLiveStatus(session);

    List<ChannelInfo> results = new ArrayList<>();

    for (Map.Entry<String, Map<String, Object>> entry : definitions.entrySet()) {
      String cname = entry.getKey();
      Map<String, Object> defn = entry.getValue();
      String ctype = String.valueOf(defn.getOrDefault("channel_type", "")).strip();
      String conname = String.valueOf(defn.getOrDefault("connection_name", "")).strip();
      String status = liveStatus.getOrDefault(cname, "INACTIVE");

      results.add(new ChannelInfo(cname, ctype, conname, true, status));
    }

    for (Map.Entry<String, String> entry : liveStatus.entrySet()) {
      if (!definitions.containsKey(entry.getKey())) {
        results.add(new ChannelInfo(entry.getKey(), "", "", false, entry.getValue()));
      }
    }

    return results;
  }

  /** Run the channel status report and print results. */
  public static List<ChannelInfo> run(MqRestSession session) {
    List<ChannelInfo> results = reportChannelStatus(session);

    System.out.printf(
        "%n%-30s %-12s %-25s %-8s %s%n", "Channel", "Type", "Connection", "Defined", "Status");
    System.out.println("-".repeat(90));

    for (ChannelInfo info : results) {
      System.out.printf(
          "%-30s %-12s %-25s %-8s %s%n",
          info.name(),
          info.channelType(),
          info.connectionName(),
          info.defined() ? "Yes" : "No",
          info.status());
    }

    List<ChannelInfo> inactive =
        results.stream().filter(c -> c.defined() && "INACTIVE".equals(c.status())).toList();
    if (!inactive.isEmpty()) {
      System.out.printf(
          "%nDefined but inactive: %s%n",
          String.join(", ", inactive.stream().map(ChannelInfo::name).toList()));
    }

    return results;
  }

  private static Map<String, Map<String, Object>> fetchDefinitions(MqRestSession session) {
    List<Map<String, Object>> channels = session.displayChannel("*", null, null, null);
    Map<String, Map<String, Object>> defs = new TreeMap<>();
    for (Map<String, Object> channel : channels) {
      String cname = String.valueOf(channel.getOrDefault("channel_name", "")).strip();
      if (!cname.isEmpty()) {
        defs.put(cname, channel);
      }
    }
    return defs;
  }

  private static Map<String, String> fetchLiveStatus(MqRestSession session) {
    Map<String, String> status = new TreeMap<>();
    try {
      List<Map<String, Object>> statuses = session.displayChstatus("*", null, null, null);
      for (Map<String, Object> entry : statuses) {
        String cname = String.valueOf(entry.getOrDefault("channel_name", "")).strip();
        String cstatus = String.valueOf(entry.getOrDefault("status", "")).strip();
        if (!cname.isEmpty()) {
          status.put(cname, cstatus);
        }
      }
    } catch (MqRestException e) {
      // No live channel status available.
    }
    return status;
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

  private ChannelStatus() {}
}
