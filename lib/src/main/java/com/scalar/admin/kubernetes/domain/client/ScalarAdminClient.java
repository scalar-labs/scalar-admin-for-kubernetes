package com.scalar.admin.kubernetes.domain.client;

import javax.annotation.Nullable;

/**
 * Client interface for communicating with Scalar Admin API.
 *
 * <p>This interface abstracts the communication with Scalar product admin interfaces, providing
 * pause and unpause operations. Implementations may use different communication protocols (e.g.,
 * standard gRPC or TLS-enabled gRPC).
 */
public interface ScalarAdminClient {

  /**
   * Pauses all pods.
   *
   * @param waitOutstandingRequests whether to wait for outstanding requests to complete
   * @param maxPauseWaitTime the maximum wait time in milliseconds, null for default
   */
  void pause(boolean waitOutstandingRequests, @Nullable Long maxPauseWaitTime);

  /**
   * Unpauses all pods.
   */
  void unpause();
}
