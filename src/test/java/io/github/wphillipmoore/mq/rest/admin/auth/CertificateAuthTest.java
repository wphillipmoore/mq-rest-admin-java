package io.github.wphillipmoore.mq.rest.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CertificateAuthTest {

  @Test
  void constructionWithBothPaths() {
    CertificateAuth auth = new CertificateAuth("/cert.pem", "/key.pem");

    assertThat(auth.certPath()).isEqualTo("/cert.pem");
    assertThat(auth.keyPath()).isEqualTo("/key.pem");
  }

  @Test
  void convenienceConstructorSetsKeyPathToNull() {
    CertificateAuth auth = new CertificateAuth("/combined.pem");

    assertThat(auth.certPath()).isEqualTo("/combined.pem");
    assertThat(auth.keyPath()).isNull();
  }

  @Test
  void nullCertPathThrowsNullPointerException() {
    assertThatThrownBy(() -> new CertificateAuth(null, "/key.pem"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("certPath");
  }

  @Test
  void nullKeyPathIsAllowed() {
    CertificateAuth auth = new CertificateAuth("/cert.pem", null);

    assertThat(auth.keyPath()).isNull();
  }

  @Test
  void equalityForSameValues() {
    CertificateAuth first = new CertificateAuth("/cert.pem", "/key.pem");
    CertificateAuth second = new CertificateAuth("/cert.pem", "/key.pem");

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void toStringContainsFieldValues() {
    CertificateAuth auth = new CertificateAuth("/cert.pem", "/key.pem");

    assertThat(auth.toString()).contains("/cert.pem");
    assertThat(auth.toString()).contains("/key.pem");
  }
}
