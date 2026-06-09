package com.scalar.admin.kubernetes.domain.model.pause;

/**
 * TLS configuration for Scalar Admin client communication.
 *
 * <p>This value object encapsulates the TLS settings required for secure communication with Scalar
 * Admin interfaces. Both CA root certificate and override authority must be provided together.
 *
 * @param caRootCert the CA root certificate for TLS verification
 * @param overrideAuthority the override authority for TLS connection
 */
public record TlsConfig(String caRootCert, String overrideAuthority) {

  /**
   * Compact constructor with validation.
   *
   * @param caRootCert the CA root certificate (required)
   * @param overrideAuthority the override authority (required)
   * @throws IllegalArgumentException if either parameter is null
   */
  public TlsConfig {
    if (caRootCert == null || caRootCert.isBlank()) {
      throw new IllegalArgumentException("caRootCert is required for TLS configuration");
    }
    if (overrideAuthority == null || overrideAuthority.isBlank()) {
      throw new IllegalArgumentException("overrideAuthority is required for TLS configuration");
    }
  }
}
