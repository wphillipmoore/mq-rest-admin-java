package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestTransportException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HttpClientTransportTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    int port = server.getAddress().getPort();
    baseUrl = "http://localhost:" + port;
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  private void startServer(int statusCode, String responseBody) {
    startServer(statusCode, responseBody, Map.of());
  }

  private void startServer(
      int statusCode, String responseBody, Map<String, String> responseHeaders) {
    server.createContext(
        "/",
        exchange -> {
          responseHeaders.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
          byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(statusCode, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
  }

  @Nested
  class PostJsonHappyPath {

    @Test
    void sendsJsonBodyWithContentTypeHeader() throws IOException {
      final String[] capturedBody = {null};
      final String[] capturedContentType = {null};
      server.createContext(
          "/",
          exchange -> {
            capturedContentType[0] = exchange.getRequestHeaders().getFirst("Content-type");
            capturedBody[0] = new String(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
          });
      server.start();

      HttpClientTransport transport = new HttpClientTransport();
      transport.postJson(baseUrl + "/test", Map.of("key", "value"), Map.of(), null, true);

      assertThat(capturedContentType[0]).isEqualTo("application/json");
      assertThat(capturedBody[0]).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void forwardsCallerHeaders() throws IOException {
      final String[] capturedHeader = {null};
      server.createContext(
          "/",
          exchange -> {
            capturedHeader[0] = exchange.getRequestHeaders().getFirst("X-Custom");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
          });
      server.start();

      HttpClientTransport transport = new HttpClientTransport();
      transport.postJson(
          baseUrl + "/test", Map.of(), Map.of("X-Custom", "custom-value"), null, true);

      assertThat(capturedHeader[0]).isEqualTo("custom-value");
    }

    @Test
    void returnsResponseStatusCode() {
      startServer(201, "{}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void returnsResponseBody() {
      startServer(200, "{\"result\":\"ok\"}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(response.body()).isEqualTo("{\"result\":\"ok\"}");
    }

    @Test
    void returnsResponseHeaders() {
      startServer(200, "{}", Map.of("X-Response", "resp-value"));

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(response.headers()).containsEntry("x-response", "resp-value");
    }

    @Test
    void acceptsNullTimeout() {
      startServer(200, "{}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void appliesNonNullTimeout() {
      startServer(200, "{}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), Duration.ofSeconds(30), true);

      assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void reusesSameTransportInstance() {
      startServer(200, "{}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse first =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);
      TransportResponse second =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(first.statusCode()).isEqualTo(200);
      assertThat(second.statusCode()).isEqualTo(200);
    }
  }

  @Nested
  class VerifyTlsBranching {

    @Test
    void verifyTlsTrueUsesPrimaryClient() {
      startServer(200, "{\"tls\":\"verified\"}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(response.body()).isEqualTo("{\"tls\":\"verified\"}");
    }

    @Test
    void verifyTlsFalseUsesNonVerifyingClient() {
      startServer(200, "{\"tls\":\"unverified\"}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, false);

      assertThat(response.body()).isEqualTo("{\"tls\":\"unverified\"}");
    }

    @Test
    void nonVerifyingClientIsLazilyCreatedAndReused() {
      startServer(200, "{}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse first =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, false);
      TransportResponse second =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, false);

      assertThat(first.statusCode()).isEqualTo(200);
      assertThat(second.statusCode()).isEqualTo(200);
    }
  }

  @Nested
  class ExceptionHandling {

    @SuppressWarnings("unchecked")
    @Test
    void wrapsIoExceptionInTransportException() throws IOException, InterruptedException {
      HttpClient mockClient = mock(HttpClient.class);
      when(mockClient.send(any(), any(HttpResponse.BodyHandler.class)))
          .thenThrow(new IOException("connection reset"));

      HttpClientTransport transport = new HttpClientTransport(mockClient);

      assertThatThrownBy(
              () -> transport.postJson("http://localhost/test", Map.of(), Map.of(), null, true))
          .isInstanceOf(MqRestTransportException.class)
          .hasMessageContaining("HTTP request failed")
          .hasCauseInstanceOf(IOException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void wrapsInterruptedExceptionAndResetsFlag() throws IOException, InterruptedException {
      HttpClient mockClient = mock(HttpClient.class);
      when(mockClient.send(any(), any(HttpResponse.BodyHandler.class)))
          .thenThrow(new InterruptedException("interrupted"));

      HttpClientTransport transport = new HttpClientTransport(mockClient);

      assertThatThrownBy(
              () -> transport.postJson("http://localhost/test", Map.of(), Map.of(), null, true))
          .isInstanceOf(MqRestTransportException.class)
          .hasMessageContaining("HTTP request interrupted")
          .hasCauseInstanceOf(InterruptedException.class);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      // Clear the interrupt flag for test cleanup
      Thread.interrupted();
    }

    @Test
    void connectionRefusedWrappedInTransportException() {
      HttpClientTransport transport = new HttpClientTransport();

      assertThatThrownBy(
              () ->
                  transport.postJson(
                      "http://localhost:1/unreachable", Map.of(), Map.of(), null, true))
          .isInstanceOf(MqRestTransportException.class)
          .hasMessageContaining("HTTP request failed")
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  class FlattenHeadersTest {

    @Test
    void flattensSingleValueHeaders() {
      Map<String, List<String>> map = new LinkedHashMap<>();
      map.put("Content-Type", List.of("application/json"));
      HttpHeaders headers = HttpHeaders.of(map, (k, v) -> true);

      Map<String, String> result = HttpClientTransport.flattenHeaders(headers);

      assertThat(result).containsEntry("Content-Type", "application/json");
    }

    @Test
    void joinsMultiValueHeadersWithCommaSpace() {
      Map<String, List<String>> map = new LinkedHashMap<>();
      map.put("Accept", List.of("text/html", "application/json"));
      HttpHeaders headers = HttpHeaders.of(map, (k, v) -> true);

      Map<String, String> result = HttpClientTransport.flattenHeaders(headers);

      assertThat(result).containsEntry("Accept", "text/html, application/json");
    }

    @Test
    void handlesEmptyHeaders() {
      HttpHeaders headers = HttpHeaders.of(Map.of(), (k, v) -> true);

      Map<String, String> result = HttpClientTransport.flattenHeaders(headers);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class CreateSslContextTest {

    @Test
    void succeedsWithTlsProtocol() {
      SSLContext context = HttpClientTransport.createSslContext("TLS");

      assertThat(context).isNotNull();
      assertThat(context.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void throwsIllegalStateExceptionForInvalidProtocol() {
      assertThatThrownBy(() -> HttpClientTransport.createSslContext("INVALID"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to create SSLContext");
    }
  }

  @Nested
  class TrustAllManagerTest {

    @Test
    void getAcceptedIssuersReturnsEmptyArray() {
      HttpClientTransport.TrustAllManager manager = new HttpClientTransport.TrustAllManager();

      assertThat(manager.getAcceptedIssuers()).isEmpty();
    }

    @Test
    void checkClientTrustedDoesNotThrow() {
      HttpClientTransport.TrustAllManager manager = new HttpClientTransport.TrustAllManager();

      manager.checkClientTrusted(null, null);
    }

    @Test
    void checkServerTrustedDoesNotThrow() {
      HttpClientTransport.TrustAllManager manager = new HttpClientTransport.TrustAllManager();

      manager.checkServerTrusted(null, null);
    }
  }

  @Nested
  class ConstructorTest {

    @Test
    void noArgConstructorCreatesWorkingTransport() {
      startServer(200, "{}");

      HttpClientTransport transport = new HttpClientTransport();
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void sslContextConstructorAccepted() throws Exception {
      startServer(200, "{}");

      SSLContext sslContext = SSLContext.getDefault();
      HttpClientTransport transport = new HttpClientTransport(sslContext);
      TransportResponse response =
          transport.postJson(baseUrl + "/test", Map.of(), Map.of(), null, true);

      assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void nullSslContextThrowsNullPointerException() {
      assertThatThrownBy(() -> new HttpClientTransport((SSLContext) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("sslContext");
    }
  }
}
