package com.scalar.admin.kubernetes.infrastructure.client;

import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import com.scalar.admin.kubernetes.domain.model.pause.TlsConfig;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Factory for creating ScalarAdminClient instances.
 *
 * <p>This factory creates appropriate ScalarAdminClient implementations based on whether TLS
 * configuration is provided. Each client is bound to a specific PauseTarget.
 */
@ThreadSafe
public class ScalarAdminClientFactory {

  /**
   * Creates a standard (non-TLS) ScalarAdminClient for the given target.
   *
   * @param target the pause target containing pods to communicate with
   * @return a new ScalarAdminClient instance without TLS
   */
  public ScalarAdminClient createClient(PauseTarget target) {
    return new ScalarAdminClientImpl(target);
  }

  /**
   * Creates a TLS-enabled ScalarAdminClient for the given target.
   *
   * @param target the pause target containing pods to communicate with
   * @param tlsConfig the TLS configuration for secure communication
   * @return a new ScalarAdminClient instance with TLS enabled
   */
  public ScalarAdminClient createClient(PauseTarget target, TlsConfig tlsConfig) {
    return new TlsScalarAdminClientImpl(target, tlsConfig);
  }
}
