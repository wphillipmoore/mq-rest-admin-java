package io.github.wphillipmoore.mq.rest.admin.auth;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Certificate-based (mTLS) authentication credentials for the MQ REST API.
 *
 * <p>Mirrors pymqrest's {@code CertificateAuth} frozen dataclass. Passed to the transport layer for
 * mTLS configuration.
 *
 * @param certPath path to the client certificate file, never null
 * @param keyPath path to the private key file, or null if combined with the certificate
 */
public record CertificateAuth(String certPath, @Nullable String keyPath) implements Credentials {

  /** Validates that certPath is non-null. keyPath may be null. */
  public CertificateAuth {
    Objects.requireNonNull(certPath, "certPath");
  }

  /**
   * Creates certificate credentials with a combined certificate and key file.
   *
   * @param certPath path to the combined certificate and key file, never null
   */
  public CertificateAuth(String certPath) {
    this(certPath, null);
  }
}
