package io.github.wphillipmoore.mq.rest.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CredentialsTest {

  @Test
  void basicAuthImplementsCredentials() {
    assertThat(new BasicAuth("user", "pass")).isInstanceOf(Credentials.class);
  }

  @Test
  void ltpaAuthImplementsCredentials() {
    assertThat(new LtpaAuth("user", "pass")).isInstanceOf(Credentials.class);
  }

  @Test
  void certificateAuthImplementsCredentials() {
    assertThat(new CertificateAuth("/cert.pem", "/key.pem")).isInstanceOf(Credentials.class);
  }

  @Test
  void sealedInterfacePermitsExactlyThreeTypes() {
    Class<?>[] permitted = Credentials.class.getPermittedSubclasses();

    assertThat(permitted).hasSize(3);
    assertThat(permitted)
        .extracting(Class::getSimpleName)
        .containsExactlyInAnyOrder("BasicAuth", "LtpaAuth", "CertificateAuth");
  }
}
