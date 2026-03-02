package io.github.wphillipmoore.mq.rest.admin.examples;

import io.github.wphillipmoore.mq.rest.admin.HttpClientTransport;
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Environment provisioner.
 *
 * <p>Defines a complete set of queues, channels, and remote queue definitions across two queue
 * managers, then verifies connectivity. Includes a teardown function to remove all provisioned
 * objects.
 *
 * <p>Requires both QM1 and QM2 to be running. Set {@code MQ_REST_BASE_URL_QM2} to the QM2 REST
 * endpoint.
 */
public final class ProvisionEnvironment {

  static final String PREFIX = "PROV";

  /** Result of the provisioning operation. */
  public record ProvisionResult(
      List<String> objectsCreated, List<String> objectsFailed, boolean verified) {

    /** Defensive copy of list fields. */
    public ProvisionResult {
      objectsCreated = List.copyOf(objectsCreated);
      objectsFailed = List.copyOf(objectsFailed);
    }
  }

  /** Provision cross-QM objects on both queue managers. */
  public static ProvisionResult provision(MqRestSession qm1, MqRestSession qm2) {
    List<String> created = new ArrayList<>();
    List<String> failed = new ArrayList<>();

    provisionLocalQueues(created, failed, qm1, qm2);
    provisionXmitQueues(created, failed, qm1, qm2);
    provisionRemoteQueues(created, failed, qm1, qm2);
    provisionChannels(created, failed, qm1, qm2);

    boolean verified = verifyObjects(qm1, qm2);
    return new ProvisionResult(List.copyOf(created), List.copyOf(failed), verified);
  }

  /** Remove all provisioned objects from both queue managers. */
  public static List<String> teardown(MqRestSession qm1, MqRestSession qm2) {
    List<String> failures = new ArrayList<>();

    for (var entry : List.of(Map.entry(qm1, "QM1"), Map.entry(qm2, "QM2"))) {
      MqRestSession session = entry.getKey();
      String label = entry.getValue();

      for (String channel : List.of(PREFIX + ".QM1.TO.QM2", PREFIX + ".QM2.TO.QM1")) {
        deleteObject(failures, session, "deleteChannel", channel, label);
      }

      for (String queue :
          List.of(
              PREFIX + ".REMOTE.TO.QM1",
              PREFIX + ".REMOTE.TO.QM2",
              PREFIX + ".QM1.TO.QM2.XMITQ",
              PREFIX + ".QM2.TO.QM1.XMITQ",
              PREFIX + ".QM1.LOCAL",
              PREFIX + ".QM2.LOCAL")) {
        deleteObject(failures, session, "deleteQueue", queue, label);
      }
    }

    return failures;
  }

  /** Provision, report, and tear down the environment. */
  public static ProvisionResult run(MqRestSession qm1, MqRestSession qm2) {
    System.out.println("\n=== Provisioning environment ===");
    ProvisionResult result = provision(qm1, qm2);

    System.out.printf("%nCreated: %d%n", result.objectsCreated().size());
    result.objectsCreated().forEach(obj -> System.out.printf("  + %s%n", obj));

    if (!result.objectsFailed().isEmpty()) {
      System.out.printf("%nFailed: %d%n", result.objectsFailed().size());
      result.objectsFailed().forEach(obj -> System.out.printf("  ! %s%n", obj));
    }

    System.out.printf("%nVerified: %s%n", result.verified());

    System.out.println("\n=== Tearing down ===");
    List<String> failures = teardown(qm1, qm2);
    if (failures.isEmpty()) {
      System.out.println("Teardown complete.");
    } else {
      System.out.printf("Teardown failures: %s%n", failures);
    }

    return result;
  }

  private static void provisionLocalQueues(
      List<String> created, List<String> failed, MqRestSession qm1, MqRestSession qm2) {
    defineObject(
        created,
        failed,
        qm1,
        "defineQlocal",
        PREFIX + ".QM1.LOCAL",
        Map.of(
            "replace", "yes",
            "default_persistence", "yes",
            "description", "provisioned local queue on QM1"));
    defineObject(
        created,
        failed,
        qm2,
        "defineQlocal",
        PREFIX + ".QM2.LOCAL",
        Map.of(
            "replace", "yes",
            "default_persistence", "yes",
            "description", "provisioned local queue on QM2"));
  }

  private static void provisionXmitQueues(
      List<String> created, List<String> failed, MqRestSession qm1, MqRestSession qm2) {
    defineObject(
        created,
        failed,
        qm1,
        "defineQlocal",
        PREFIX + ".QM1.TO.QM2.XMITQ",
        Map.of("replace", "yes", "usage", "XMITQ", "description", "xmit queue QM1 to QM2"));
    defineObject(
        created,
        failed,
        qm2,
        "defineQlocal",
        PREFIX + ".QM2.TO.QM1.XMITQ",
        Map.of("replace", "yes", "usage", "XMITQ", "description", "xmit queue QM2 to QM1"));
  }

  private static void provisionRemoteQueues(
      List<String> created, List<String> failed, MqRestSession qm1, MqRestSession qm2) {
    defineObject(
        created,
        failed,
        qm1,
        "defineQremote",
        PREFIX + ".REMOTE.TO.QM2",
        Map.of(
            "replace", "yes",
            "remote_queue_name", PREFIX + ".QM2.LOCAL",
            "remote_queue_manager_name", "QM2",
            "transmission_queue_name", PREFIX + ".QM1.TO.QM2.XMITQ",
            "description", "remote queue QM1 to QM2"));
    defineObject(
        created,
        failed,
        qm2,
        "defineQremote",
        PREFIX + ".REMOTE.TO.QM1",
        Map.of(
            "replace", "yes",
            "remote_queue_name", PREFIX + ".QM1.LOCAL",
            "remote_queue_manager_name", "QM1",
            "transmission_queue_name", PREFIX + ".QM2.TO.QM1.XMITQ",
            "description", "remote queue QM2 to QM1"));
  }

  private static void provisionChannels(
      List<String> created, List<String> failed, MqRestSession qm1, MqRestSession qm2) {
    defineObject(
        created,
        failed,
        qm1,
        "defineChannel",
        PREFIX + ".QM1.TO.QM2",
        Map.of(
            "replace", "yes",
            "channel_type", "SDR",
            "transport_type", "TCP",
            "connection_name", "qm2(1414)",
            "transmission_queue_name", PREFIX + ".QM1.TO.QM2.XMITQ",
            "description", "sender QM1 to QM2"));
    defineObject(
        created,
        failed,
        qm2,
        "defineChannel",
        PREFIX + ".QM1.TO.QM2",
        Map.of(
            "replace", "yes",
            "channel_type", "RCVR",
            "transport_type", "TCP",
            "description", "receiver QM1 to QM2"));
    defineObject(
        created,
        failed,
        qm2,
        "defineChannel",
        PREFIX + ".QM2.TO.QM1",
        Map.of(
            "replace", "yes",
            "channel_type", "SDR",
            "transport_type", "TCP",
            "connection_name", "qm1(1414)",
            "transmission_queue_name", PREFIX + ".QM2.TO.QM1.XMITQ",
            "description", "sender QM2 to QM1"));
    defineObject(
        created,
        failed,
        qm1,
        "defineChannel",
        PREFIX + ".QM2.TO.QM1",
        Map.of(
            "replace", "yes",
            "channel_type", "RCVR",
            "transport_type", "TCP",
            "description", "receiver QM2 to QM1"));
  }

  private static boolean verifyObjects(MqRestSession qm1, MqRestSession qm2) {
    try {
      List<Map<String, Object>> qm1Queues = qm1.displayQueue(PREFIX + ".*", null, null, null);
      List<Map<String, Object>> qm2Queues = qm2.displayQueue(PREFIX + ".*", null, null, null);
      return qm1Queues.size() >= 3 && qm2Queues.size() >= 3;
    } catch (MqRestException e) {
      return false;
    }
  }

  private static void defineObject(
      List<String> created,
      List<String> failed,
      MqRestSession session,
      String methodName,
      String name,
      Map<String, Object> parameters) {
    String label = session.getQmgrName() + "/" + name;
    try {
      Method method =
          MqRestSession.class.getMethod(methodName, String.class, Map.class, List.class);
      method.invoke(session, name, parameters, null);
      created.add(label);
    } catch (InvocationTargetException e) {
      failed.add(label);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Method not found: " + methodName, e);
    }
  }

  private static void deleteObject(
      List<String> failures, MqRestSession session, String methodName, String name, String label) {
    try {
      Method method =
          MqRestSession.class.getMethod(methodName, String.class, Map.class, List.class);
      method.invoke(session, name, null, null);
    } catch (InvocationTargetException e) {
      failures.add(label + "/" + name);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Method not found: " + methodName, e);
    }
  }

  /** Entry point. */
  public static void main(String[] args) {
    HttpClientTransport transport = new HttpClientTransport();

    MqRestSession qm1Session =
        new MqRestSession.Builder(
                env("MQ_REST_BASE_URL", "https://localhost:9453/ibmmq/rest/v2"),
                "QM1",
                new BasicAuth(env("MQ_ADMIN_USER", "mqadmin"), env("MQ_ADMIN_PASSWORD", "mqadmin")))
            .transport(transport)
            .verifyTls(false)
            .build();

    MqRestSession qm2Session =
        new MqRestSession.Builder(
                env("MQ_REST_BASE_URL_QM2", "https://localhost:9454/ibmmq/rest/v2"),
                "QM2",
                new BasicAuth(env("MQ_ADMIN_USER", "mqadmin"), env("MQ_ADMIN_PASSWORD", "mqadmin")))
            .transport(transport)
            .verifyTls(false)
            .build();

    run(qm1Session, qm2Session);
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return value != null ? value : defaultValue;
  }

  private ProvisionEnvironment() {}
}
