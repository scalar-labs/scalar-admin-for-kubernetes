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
  private Instant startTime;
  private Instant endTime;

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
    PauseFailedException pauseFailedException = null;
    try {
      pauseInternal(requestCoordinator, pauseDuration, maxPauseWaitTime);
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

    // Prepare error messages for each process.
    String unpauseErrorMessage =
        String.format(
            "Unpause operation failed. Scalar products might still be in a paused state. You"
                + " must restart related pods by using the `kubectl rollout restart deployment"
                + " %s` command to unpause all pods. ",
            targetBeforePause.getDeployment().getMetadata().getName());
    String pauseErrorMessage =
        "Pause operation failed. You cannot use the backup that was taken during this pause"
            + " duration. You need to retry the pause operation from the beginning to"
            + " take a backup. ";
    String getTargetAfterPauseErrorMessage =
        "Failed to find the target pods to examine if the targets pods were updated during"
            + " paused. ";
    String statusCheckErrorMessage =
        "Status check failed. You cannot use the backup that was taken during this pause"
            + " duration. You need to retry the pause operation from the beginning to"
            + " take a backup. ";
    String statusDifferentErrorMessage =
        "The target pods were updated during the pause duration. You cannot use the backup that"
            + " was taken during this pause duration. ";
    String unpauseFailedButPauseOkErrorMessage =
        String.format(
            "Note that the pause operations for taking backup succeeded. You can use a backup that"
                + " was taken during this pause duration: Start Time = %s, End Time = %s. ",
            startTime, endTime);

    // Get pods and deployment information after pause.
    TargetSnapshot targetAfterPause;
    PauserException pauserException;
    try {
      targetAfterPause = getTarget();
    } catch (Exception e) {
      pauserException = new PauserException(getTargetAfterPauseErrorMessage, e);
      if (unpauseFailedException == null) {
        throw pauserException;
      } else {
        throw new UnpauseFailedException(unpauseErrorMessage, pauserException);
      }
    }

    // Check if pods and deployment information are the same between before pause and after pause.
    boolean compareTargetSuccessful;
    StatusCheckFailedException statusCheckFailedException;
    try {
      compareTargetSuccessful = compareTargetStatus(targetBeforePause, targetAfterPause);
    } catch (Exception e) {
      statusCheckFailedException = new StatusCheckFailedException(statusCheckErrorMessage, e);
      if (unpauseFailedException == null) {
        throw statusCheckFailedException;
      } else {
        throw new UnpauseFailedException(unpauseErrorMessage, statusCheckFailedException);
      }
    }

    // If both the pause operation and status check succeeded, you can use the backup that was taken
    // during the pause duration.
    boolean isPauseOk = (pauseFailedException == null) && compareTargetSuccessful;

    // Create an error message if any of the operations failed.
    StringBuilder errorMessageBuilder = new StringBuilder();
    if (unpauseFailedException != null) {
      errorMessageBuilder.append(unpauseErrorMessage);
      if (isPauseOk) {
        errorMessageBuilder.append(unpauseFailedButPauseOkErrorMessage);
      }
    }
    if (pauseFailedException != null) {
      errorMessageBuilder.append(pauseErrorMessage);
    }
    if (!compareTargetSuccessful) {
      errorMessageBuilder.append(statusDifferentErrorMessage);
    }
    String errorMessage = errorMessageBuilder.toString();

    // Return the final result based on each process.
    if (unpauseFailedException
        != null) { // Unpause issue is the most critical because it might cause system down.
      throw new UnpauseFailedException(errorMessage, unpauseFailedException);
    } else if (pauseFailedException
        != null) { // Pause Failed is second priority because pause issue might be caused by
      // configuration error.
      throw new PauseFailedException(errorMessage, pauseFailedException);
    } else if (!compareTargetSuccessful) { // Status check failed is third priority because this
      // issue might be caused by temporary issue, for example, pod restarts.
      throw new PauseFailedException(errorMessage);
    } else { // All operations succeeded.
      return new PausedDuration(startTime, endTime);
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
  void pauseInternal(
      RequestCoordinator requestCoordinator, int pauseDuration, @Nullable Long maxPauseWaitTime) {

    requestCoordinator.pause(true, maxPauseWaitTime);
    startTime = Instant.now();
    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS);
    endTime = Instant.now();
  }

  @VisibleForTesting
  boolean compareTargetStatus(TargetSnapshot before, TargetSnapshot after) {
    return before.getStatus().equals(after.getStatus());
  }
}
