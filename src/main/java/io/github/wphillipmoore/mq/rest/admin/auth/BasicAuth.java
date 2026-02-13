package io.github.wphillipmoore.mq.rest.admin.auth;

import java.util.Objects;

/**
 * Basic authentication credentials for the MQ REST API.
 *
 * <p>Mirrors pymqrest's {@code BasicAuth} frozen dataclass. Used to construct an {@code
 * Authorization: Basic} header.
 *
 * @param username the username, never null
 * @param password the password, never null
 */
public record BasicAuth(String username, String password) implements Credentials {

  /** Validates that username and password are non-null. */
  public BasicAuth {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(password, "password");
  }
}
