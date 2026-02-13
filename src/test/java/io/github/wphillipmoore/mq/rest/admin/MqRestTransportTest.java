package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MqRestTransportTest {

  @Test
  void stubImplementationSatisfiesContract() {
    MqRestTransport transport =
        (url, payload, headers, timeout, verifyTls) -> new TransportResponse(200, "{}", Map.of());

    TransportResponse response =
        transport.postJson(
            "https://localhost:9443/ibmmq/rest/v1/admin/qmgr",
            Map.of("type", "runCommand"),
            Map.of("Content-Type", "application/json"),
            Duration.ofSeconds(30),
            true);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{}");
  }
}
