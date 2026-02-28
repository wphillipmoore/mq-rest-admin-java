package io.github.wphillipmoore.mq.rest.admin.examples;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wphillipmoore.mq.rest.admin.HttpClientTransport;
import io.github.wphillipmoore.mq.rest.admin.MqRestSession;
import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for the runnable example scripts.
 *
 * <p>Gated by the {@code MQ_REST_ADMIN_RUN_INTEGRATION} environment variable.
 */
@EnabledIfEnvironmentVariable(named = "MQ_REST_ADMIN_RUN_INTEGRATION", matches = ".+")
class ExamplesIT {

  static final String REST_BASE_URL =
      env("MQ_REST_BASE_URL", "https://localhost:9453/ibmmq/rest/v2");
  static final String QM2_REST_BASE_URL =
      env("MQ_REST_BASE_URL_QM2", "https://localhost:9454/ibmmq/rest/v2");
  static final String ADMIN_USER = env("MQ_ADMIN_USER", "mqadmin");
  static final String ADMIN_PASSWORD = env("MQ_ADMIN_PASSWORD", "mqadmin");

  MqRestSession qm1Session() {
    return new MqRestSession.Builder(
            REST_BASE_URL, "QM1", new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
        .transport(new HttpClientTransport())
        .verifyTls(false)
        .build();
  }

  MqRestSession qm2Session() {
    return new MqRestSession.Builder(
            QM2_REST_BASE_URL, "QM2", new BasicAuth(ADMIN_USER, ADMIN_PASSWORD))
        .transport(new HttpClientTransport())
        .verifyTls(false)
        .build();
  }

  // ---------------------------------------------------------------------------
  // Health check
  // ---------------------------------------------------------------------------

  @Test
  void healthCheckQm1() {
    var result = HealthCheck.checkHealth(qm1Session());

    assertThat(result.reachable()).isTrue();
    assertThat(result.passed()).isTrue();
    assertThat(result.qmgrName()).isEqualTo("QM1");
  }

  @Test
  void healthCheckQm2() {
    var result = HealthCheck.checkHealth(qm2Session());

    assertThat(result.reachable()).isTrue();
    assertThat(result.passed()).isTrue();
    assertThat(result.qmgrName()).isEqualTo("QM2");
  }

  // ---------------------------------------------------------------------------
  // Queue depth monitor
  // ---------------------------------------------------------------------------

  @Test
  void queueDepthMonitor() {
    var results = QueueDepthMonitor.monitorQueueDepths(qm1Session(), 80.0);

    assertThat(results).isNotEmpty();
    assertThat(results).anyMatch(q -> "DEV.QLOCAL".equals(q.name()));
  }

  // ---------------------------------------------------------------------------
  // Channel status
  // ---------------------------------------------------------------------------

  @Test
  void channelStatusReport() {
    var results = ChannelStatus.reportChannelStatus(qm1Session());

    assertThat(results).isNotEmpty();
    assertThat(results).anyMatch(c -> "DEV.SVRCONN".equals(c.name()));
  }

  // ---------------------------------------------------------------------------
  // DLQ inspector
  // ---------------------------------------------------------------------------

  @Test
  void dlqInspector() {
    var report = DlqInspector.inspectDlq(qm1Session());

    assertThat(report.configured()).isTrue();
    assertThat(report.dlqName()).isEqualTo("DEV.DEAD.LETTER");
    assertThat(report.currentDepth()).isZero();
  }

  // ---------------------------------------------------------------------------
  // Queue status
  // ---------------------------------------------------------------------------

  @Test
  void queueStatusHandles() {
    var queueHandles = QueueStatus.reportQueueHandles(qm1Session());

    assertThat(queueHandles).isNotNull();
  }

  @Test
  void connectionHandles() {
    var connHandles = QueueStatus.reportConnectionHandles(qm1Session());

    assertThat(connHandles).isNotNull();
  }

  // ---------------------------------------------------------------------------
  // Provision and teardown
  // ---------------------------------------------------------------------------

  @Test
  void provisionAndTeardown() {
    MqRestSession qm1 = qm1Session();
    MqRestSession qm2 = qm2Session();

    var result = ProvisionEnvironment.provision(qm1, qm2);

    assertThat(result.objectsCreated()).isNotEmpty();
    assertThat(result.verified()).isTrue();

    List<String> failures = ProvisionEnvironment.teardown(qm1, qm2);

    assertThat(failures).isEmpty();
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return value != null ? value : defaultValue;
  }
}
