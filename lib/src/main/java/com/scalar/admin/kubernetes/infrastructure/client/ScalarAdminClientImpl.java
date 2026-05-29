package com.scalar.admin.kubernetes.infrastructure.client;

import com.scalar.admin.RequestCoordinator;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Standard (non-TLS) implementation of ScalarAdminClient.
 *
 * <p>This implementation uses the standard RequestCoordinator for communicating with Scalar Admin
 * interfaces without TLS encryption.
 */
@ThreadSafe
public class ScalarAdminClientImpl implements ScalarAdminClient {

  private final RequestCoordinator requestCoordinator;

  /**
   * Creates a ScalarAdminClientImpl for the given pause target.
   *
   * @param target the pause target containing pods to communicate with
   */
  public ScalarAdminClientImpl(PauseTarget target) {
    if (target == null) {
      throw new IllegalArgumentException("PauseTarget must not be null");
    }
    this.requestCoordinator = new RequestCoordinator(buildAddressList(target));
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
