package com.scalar.admin.kubernetes;

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

  private static final int MAX_UNPAUSE_RETRY_COUNT = 3;

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
      target = targetSelector.select();
    } catch (Exception e) {
      throw new PauserException("Failed to find the target pods to pause.", e);
    }

    RequestCoordinator coordinator = getRequestCoordinator(target);

    coordinator.pause(true, maxPauseWaitTime);

    Instant startTime = Instant.now();

    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS);

    Instant endTime = Instant.now();

    unpauseWithRetry(coordinator, MAX_UNPAUSE_RETRY_COUNT, target);

    TargetSnapshot targetAfterPause;
    try {
      targetAfterPause = targetSelector.select();
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

  private void unpauseWithRetry(
      RequestCoordinator coordinator, int maxRetryCount, TargetSnapshot target) {
    int retryCounter = 0;

    while (true) {
      try {
        coordinator.unpause();
        return;
      } catch (Exception e) {
        if (++retryCounter >= maxRetryCount) {
          logger.warn(
              "Failed to unpause Scalar product. They are still in paused. You must restart related"
                  + " pods by using the `kubectl rollout restart deployment {}`"
                  + " command to unpase all pods.",
              target.getDeployment().getMetadata().getName());
          return;
        }
      }
    }
  }

  RequestCoordinator getRequestCoordinator(TargetSnapshot target) {
    return new RequestCoordinator(
        target.getPods().stream()
            .map(p -> new InetSocketAddress(p.getStatus().getPodIP(), target.getAdminPort()))
            .collect(Collectors.toList()));
  }
}
