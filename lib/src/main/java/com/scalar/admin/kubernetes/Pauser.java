package com.scalar.admin.kubernetes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a pause operation for Scalar product pods in a Kubernetes cluster. The
 * pause operation consists of the following steps:
 *
 * <ol>
 *   <li>Find the target pods to pause.
 *   <li>Pause the target pods.
 *   <li>Wait for the specified duration.
 *   <li>Unpause the target pods.
 *   <li>Check if the target pods were updated during the pause operation.
 * </ol>
 *
 * Please note that this class is not thread-safe because the `pause` method causes side effects in
 * the states of target pods.
 */
@NotThreadSafe
public class Pauser {

  @VisibleForTesting static final int MAX_UNPAUSE_RETRY_COUNT = 3;

  private final Logger logger = LoggerFactory.getLogger(Pauser.class);
  private final TargetSelector targetSelector;

  /**
   * @param namespace The namespace where the pods are deployed.
   * @param helmReleaseName The Helm release name used to deploy the pods.
   * @throws PauserException when the default Kubernetes client fails to be set.
   */
  public Pauser(String namespace, String helmReleaseName) throws PauserException {
    if (namespace == null) {
      throw new IllegalArgumentException("namespace is required");
    }

    if (helmReleaseName == null) {
      throw new IllegalArgumentException("helmReleaseName is required");
    }

    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new PauserException("Failed to set default Kubernetes client.", e);
    }

    targetSelector =
        new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, helmReleaseName);
  }

  /**
   * @param pauseDuration The duration to pause in milliseconds.
   * @param maxPauseWaitTime The max wait time (in milliseconds) until Scalar products drain
   *     outstanding requests before they pause.
   * @throws PauserException when the pause operation fails.
   * @return The start and end time of the pause operation.
   */
  public PausedDuration pause(int pauseDuration, @Nullable Long maxPauseWaitTime)
      throws PauserException {
    if (pauseDuration < 1) {
      throw new IllegalArgumentException(
          "pauseDuration is required to be greater than 0 millisecond.");
    }

    TargetSnapshot target;
    try {
      target = getTarget();
    } catch (Exception e) {
      throw new PauserException("Failed to find the target pods to pause.", e);
    }

    RequestCoordinator coordinator;
    try {
      coordinator = getRequestCoordinator(target);
    } catch (Exception e) {
      throw new PauserException("Failed to initialize the coordinator.", e);
    }

    Instant startTime;
    Instant endTime;
    try {
      coordinator.pause(true, maxPauseWaitTime);

      startTime = Instant.now();

      Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS);

      endTime = Instant.now();

      unpauseWithRetry(coordinator, MAX_UNPAUSE_RETRY_COUNT, target);

    } catch (Exception e) {
      try {
        unpauseWithRetry(coordinator, MAX_UNPAUSE_RETRY_COUNT, target);
      } catch (PauserException ex) {
        throw new PauserException("unpauseWithRetry() method failed twice.", e);
      } catch (Exception ex) {
        throw new PauserException(
            "unpauseWithRetry() method failed twice due to unexpected exception.", e);
      }
      throw new PauserException(
          "The pause operation failed for some reason. However, the unpause operation succeeded"
              + " afterward. Currently, the scalar products are running with the unpause status."
              + " You should retry the pause operation to ensure proper backup.",
          e);
    }

    TargetSnapshot targetAfterPause;
    try {
      targetAfterPause = getTarget();
    } catch (Exception e) {
      throw new PauserException(
          "Failed to find the target pods to examine if the targets pods were updated during"
              + " paused.",
          e);
    }

    if (!target.getStatus().equals(targetAfterPause.getStatus())) {
      throw new PauserException("The target pods were updated during paused. Please retry.");
    }

    return new PausedDuration(startTime, endTime);
  }

  @VisibleForTesting
  void unpauseWithRetry(RequestCoordinator coordinator, int maxRetryCount, TargetSnapshot target)
      throws PauserException {
    int retryCounter = 0;

    while (true) {
      try {
        coordinator.unpause();
        return;
      } catch (Exception e) {
        if (++retryCounter >= maxRetryCount) {
          // Users who directly utilize this library, bypassing our CLI, are responsible for proper
          // exception handling. However, this scenario represents a critical issue. Consequently,
          // we output the error message here regardless of whether the calling code handles the
          // exception.
          logger.error(
              "Failed to unpause Scalar product. They are still in paused. You must restart related"
                  + " pods by using the `kubectl rollout restart deployment {}`"
                  + " command to unpause all pods.",
              target.getDeployment().getMetadata().getName());
          // In our CLI, we catch this exception and output the message as an error on the CLI side.
          throw new PauserException(
              String.format(
                  "Failed to unpause Scalar product. They are still in paused. You must restart"
                      + " related pods by using the `kubectl rollout restart deployment %s` command"
                      + " to unpause all pods.",
                  target.getDeployment().getMetadata().getName()),
              e);
        }
      }
    }
  }

  @VisibleForTesting
  TargetSnapshot getTarget() throws PauserException {
    return targetSelector.select();
  }

  @VisibleForTesting
  RequestCoordinator getRequestCoordinator(TargetSnapshot target) {
    return new RequestCoordinator(
        target.getPods().stream()
            .map(p -> new InetSocketAddress(p.getStatus().getPodIP(), target.getAdminPort()))
            .collect(Collectors.toList()));
  }
}
