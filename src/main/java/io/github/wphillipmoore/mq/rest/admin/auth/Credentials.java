package io.github.wphillipmoore.mq.rest.admin.auth;

/**
 * Sealed credential type for MQ REST API authentication.
 *
 * <p>Mirrors pymqrest's {@code Credentials} union type. The session dispatches on the concrete type
 * using {@code instanceof} pattern matching.
 */
public sealed interface Credentials permits BasicAuth, LtpaAuth, CertificateAuth {}
