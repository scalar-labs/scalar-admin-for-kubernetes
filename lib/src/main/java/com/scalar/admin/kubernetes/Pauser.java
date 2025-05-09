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

  private final TargetSelector targetSelector;

  private static final String UNPAUSE_ERROR_MESSAGE =
      "Unpause operation failed. Scalar products might still be in a paused state. You"
          + " must restart related pods by using the `kubectl rollout restart deployment"
          + " <DEPLOYMENT_NAME>` command to unpause all pods. ";
  private static final String PAUSE_ERROR_MESSAGE =
      "Pause operation failed. You cannot use the backup that was taken during this pause"
          + " duration. You need to retry the pause operation from the beginning to"
          + " take a backup. ";
  private static final String GET_TARGET_AFTER_PAUSE_ERROR_MESSAGE =
      "Failed to find the target pods to examine if the targets pods were updated during"
          + " paused. ";
  private static final String STATUS_CHECK_ERROR_MESSAGE =
      "Status check failed. You cannot use the backup that was taken during this pause"
          + " duration. You need to retry the pause operation from the beginning to"
          + " take a backup. ";
  private static final String STATUS_DIFFERENT_ERROR_MESSAGE =
      "The target pods were updated during the pause duration. You cannot use the backup that"
          + " was taken during this pause duration. ";

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

    // Get pods and deployment information before pause.
    TargetSnapshot targetBeforePause;
    try {
      targetBeforePause = getTarget();
    } catch (Exception e) {
      throw new PauserException("Failed to find the target pods to pause.", e);
    }

    // Get RequestCoordinator of Scalar Admin to pause.
    RequestCoordinator requestCoordinator;
    try {
      requestCoordinator = getRequestCoordinator(targetBeforePause);
    } catch (Exception e) {
      throw new PauserException("Failed to initialize the request coordinator.", e);
    }

    // Run a pause operation.
    PausedDuration pausedDuration = null;
    PauseFailedException pauseFailedException = null;
    try {
      pausedDuration = pauseInternal(requestCoordinator, pauseDuration, maxPauseWaitTime);
    } catch (Exception e) {
      pauseFailedException = new PauseFailedException("Pause operation failed.", e);
    }

    // Run an unpause operation.
    UnpauseFailedException unpauseFailedException = null;
    try {
      unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
    } catch (Exception e) {
      unpauseFailedException = new UnpauseFailedException("Unpause operation failed.", e);
    }

    // Get pods and deployment information after pause.
    TargetSnapshot targetAfterPause;
    try {
      targetAfterPause = getTarget();
    } catch (Exception e) {
      PauserException pauserException =
          new PauserException(GET_TARGET_AFTER_PAUSE_ERROR_MESSAGE, e);
      if (unpauseFailedException == null) {
        throw pauserException;
      } else {
        throw new UnpauseFailedException(UNPAUSE_ERROR_MESSAGE, pauserException);
      }
    }

    // Check if pods and deployment information are the same between before pause and after pause.
    boolean isTargetStatusEqual;
    try {
      isTargetStatusEqual = targetStatusEquals(targetBeforePause, targetAfterPause);
    } catch (Exception e) {
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(STATUS_CHECK_ERROR_MESSAGE, e);
      if (unpauseFailedException == null) {
        throw statusCheckFailedException;
      } else {
        throw new UnpauseFailedException(UNPAUSE_ERROR_MESSAGE, statusCheckFailedException);
      }
    }

    // Create an error message if any of the operations failed.
    String errorMessage =
        createErrorMessage(unpauseFailedException, pauseFailedException, isTargetStatusEqual);

    // Return the final result based on each process.
    if (unpauseFailedException != null) { // Unpause Failed.
      // Unpause issue is the most critical because it might cause system down.
      throw new UnpauseFailedException(errorMessage, unpauseFailedException);
    } else if (pauseFailedException != null) { // Pause Failed.
      // Pause Failed is second priority because pause issue might be caused by configuration error.
      throw new PauseFailedException(errorMessage, pauseFailedException);
    } else if (!isTargetStatusEqual) { // Target status changed.
      // Target status changed is third priority because this issue might be caused by temporary
      // issue, for example, pod restarts.
      throw new PauseFailedException(errorMessage);
    } else {
      // All operations succeeded.
      return pausedDuration;
    }
  }

  @VisibleForTesting
  void unpauseWithRetry(RequestCoordinator coordinator, int maxRetryCount) {
    int retryCounter = 0;
    while (true) {
      try {
        coordinator.unpause();
        return;
      } catch (Exception e) {
        if (++retryCounter >= maxRetryCount) {
          throw e;
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

  @VisibleForTesting
  PausedDuration pauseInternal(
      RequestCoordinator requestCoordinator, int pauseDuration, @Nullable Long maxPauseWaitTime) {
    requestCoordinator.pause(true, maxPauseWaitTime);
    Instant startTime = Instant.now();
    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS);
    Instant endTime = Instant.now();
    return new PausedDuration(startTime, endTime);
  }

  @VisibleForTesting
  boolean targetStatusEquals(TargetSnapshot before, TargetSnapshot after) {
    return before.getStatus().equals(after.getStatus());
  }

  private String createErrorMessage(
      UnpauseFailedException unpauseFailedException,
      PauseFailedException pauseFailedException,
      boolean isTargetStatusEqual) {
    StringBuilder errorMessageBuilder = new StringBuilder();
    if (unpauseFailedException != null) {
      errorMessageBuilder.append(UNPAUSE_ERROR_MESSAGE);
    }
    if (pauseFailedException != null) {
      errorMessageBuilder.append(PAUSE_ERROR_MESSAGE);
    }
    if (!isTargetStatusEqual) {
      errorMessageBuilder.append(STATUS_DIFFERENT_ERROR_MESSAGE);
    }
    return errorMessageBuilder.toString();
  }
}
