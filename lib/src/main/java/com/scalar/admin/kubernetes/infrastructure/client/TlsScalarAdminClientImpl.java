package com.scalar.admin.kubernetes.infrastructure.client;

import com.scalar.admin.TlsRequestCoordinator;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import com.scalar.admin.kubernetes.domain.model.pause.TlsConfig;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * TLS-enabled implementation of ScalarAdminClient.
 *
 * <p>This implementation uses TlsRequestCoordinator for secure communication with Scalar Admin
 * interfaces using TLS encryption.
 */
@ThreadSafe
public class TlsScalarAdminClientImpl implements ScalarAdminClient {

  private final TlsRequestCoordinator requestCoordinator;

  /**
   * Creates a TlsScalarAdminClientImpl for the given pause target with TLS configuration.
   *
   * @param target the pause target containing pods to communicate with
   * @param tlsConfig the TLS configuration
   */
  public TlsScalarAdminClientImpl(PauseTarget target, TlsConfig tlsConfig) {
    if (target == null) {
      throw new IllegalArgumentException("PauseTarget must not be null");
    }
    if (tlsConfig == null) {
      throw new IllegalArgumentException("TlsConfig must not be null");
    }
    this.requestCoordinator =
        new TlsRequestCoordinator(
            buildAddressList(target), tlsConfig.caRootCert(), tlsConfig.overrideAuthority());
  }

  @Override
  public void pause(boolean waitOutstandingRequests, @Nullable Long maxPauseWaitTime) {
    requestCoordinator.pause(waitOutstandingRequests, maxPauseWaitTime);
  }

  @Override
  public void unpause() {
    requestCoordinator.unpause();
  }

  /**
   * Builds a list of InetSocketAddress from the pause target.
   *
   * @param target the pause target
   * @return list of addresses (pod IP + admin port)
   */
  private List<InetSocketAddress> buildAddressList(PauseTarget target) {
    return target.pods().stream()
        .map(pod -> new InetSocketAddress(pod.getStatus().getPodIP(), target.adminPort()))
        .collect(Collectors.toList());
  }
}
