package com.scalar.admin.kubernetes.domain.client;

import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import com.scalar.admin.kubernetes.domain.model.pause.TlsConfig;

/**
 * Factory interface for creating ScalarAdminClient instances.
 *
 * <p>This interface abstracts the creation of ScalarAdminClient implementations, allowing the
 * application layer to create clients without depending on infrastructure-specific implementations.
 */
public interface ScalarAdminClientFactory {

  /**
   * Creates a standard (non-TLS) ScalarAdminClient for the given target.
   *
   * @param target the pause target containing pods to communicate with
   * @return a new ScalarAdminClient instance without TLS
   */
  ScalarAdminClient createClient(PauseTarget target);

  /**
   * Creates a TLS-enabled ScalarAdminClient for the given target.
   *
   * @param target the pause target containing pods to communicate with
   * @param tlsConfig the TLS configuration for secure communication
   * @return a new ScalarAdminClient instance with TLS enabled
   */
  ScalarAdminClient createClient(PauseTarget target, TlsConfig tlsConfig);
}
