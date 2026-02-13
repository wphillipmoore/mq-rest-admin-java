package io.github.wphillipmoore.mq.rest.admin.auth;

import java.util.Objects;

/**
 * LTPA authentication credentials for the MQ REST API.
 *
 * <p>Mirrors pymqrest's {@code LTPAAuth} frozen dataclass. The session performs a login POST at
 * construction time and stores the {@code LtpaToken2} cookie for subsequent requests.
 *
 * @param username the username for LTPA login, never null
 * @param password the password for LTPA login, never null
 */
public record LtpaAuth(String username, String password) implements Credentials {

  /** Validates that username and password are non-null. */
  public LtpaAuth {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(password, "password");
  }
}
