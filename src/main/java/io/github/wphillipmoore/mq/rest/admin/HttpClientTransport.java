package io.github.wphillipmoore.mq.rest.admin;

import com.google.gson.Gson;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestTransportException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.jspecify.annotations.Nullable;

/**
 * JDK {@link HttpClient}-based implementation of {@link MqRestTransport}.
 *
 * <p>Mirrors pymqrest's {@code RequestsTransport}. Uses {@link java.net.http.HttpClient} for HTTP
 * communication and Gson for JSON serialization, requiring zero additional runtime dependencies
 * beyond Gson.
 */
public final class HttpClientTransport implements MqRestTransport {

  private final Gson gson = new Gson();
  private final HttpClient client;
  private @Nullable HttpClient nonVerifyingClient;

  /** Creates a transport with a default TLS-verifying {@link HttpClient}. */
  public HttpClientTransport() {
    this.client = HttpClient.newHttpClient();
  }

  /**
   * Creates a transport with a custom {@link SSLContext} for mutual TLS.
   *
   * @param sslContext the SSL context to use
   */
  public HttpClientTransport(SSLContext sslContext) {
    Objects.requireNonNull(sslContext, "sslContext");
    this.client = HttpClient.newBuilder().sslContext(sslContext).build();
  }

  /**
   * Creates a transport with an injected {@link HttpClient}. Package-private for testing.
   *
   * @param client the HTTP client to use
   */
  HttpClientTransport(HttpClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  @Override
  @SuppressWarnings("PMD.CloseResource") // HttpClient is managed by this transport, not disposable
  public TransportResponse postJson(
      String url,
      Map<String, Object> payload,
      Map<String, String> headers,
      @Nullable Duration timeout,
      boolean verifyTls) {
    HttpClient activeClient = verifyTls ? client : getNonVerifyingClient();
    String json = gson.toJson(payload);

    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));

    headers.forEach(requestBuilder::header);

    if (timeout != null) {
      requestBuilder.timeout(timeout);
    }

    HttpResponse<String> response;
    try {
      response = activeClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new MqRestTransportException("HTTP request failed", url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MqRestTransportException("HTTP request interrupted", url, e);
    }

    return new TransportResponse(
        response.statusCode(), response.body(), flattenHeaders(response.headers()));
  }

  private synchronized HttpClient getNonVerifyingClient() {
    if (nonVerifyingClient == null) {
      SSLContext sslContext = createSslContext("TLS");
      nonVerifyingClient = HttpClient.newBuilder().sslContext(sslContext).build();
    }
    return nonVerifyingClient;
  }

  /**
   * Creates an {@link SSLContext} with a trust-all manager.
   *
   * @param protocol the SSL protocol name (e.g. "TLS")
   * @return an initialized SSLContext that trusts all certificates
   * @throws IllegalStateException if the protocol is not available
   */
  static SSLContext createSslContext(String protocol) {
    try {
      SSLContext sslContext = SSLContext.getInstance(protocol);
      sslContext.init(null, new TrustManager[] {new TrustAllManager()}, null);
      return sslContext;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to create SSLContext", e);
    }
  }

  /**
   * Flattens {@link HttpHeaders} multi-value map to single-value map per RFC 9110 section 5.3.
   *
   * <p>Multiple values for the same header name are joined with {@code ", "}.
   *
   * @param httpHeaders the HTTP response headers
   * @return a flattened string-to-string header map
   */
  static Map<String, String> flattenHeaders(HttpHeaders httpHeaders) {
    Map<String, String> result = new LinkedHashMap<>();
    httpHeaders.map().forEach((name, values) -> result.put(name, String.join(", ", values)));
    return result;
  }

  /**
   * An {@link X509TrustManager} that accepts all certificates. Used when TLS verification is
   * disabled.
   */
  static final class TrustAllManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
      // Accept all client certificates
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      // Accept all server certificates
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
