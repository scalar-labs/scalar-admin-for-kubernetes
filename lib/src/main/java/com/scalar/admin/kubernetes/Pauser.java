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
import java.util.Objects;
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
  private Instant startTime;
  private Instant endTime;
  private PauseFailedException pauseFailedException;
  private UnpauseFailedException unpauseFailedException;
  private StatusCheckFailedException statusCheckFailedException;
  private boolean pauseSuccessful = false;
  private boolean unpauseSuccessful = false;
  private boolean compareTargetSuccessful = false;

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
      throws PauserException, UnpauseFailedException, PauseFailedException,
          StatusCheckFailedException {
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

    // Run pause operation.
    try {
      pauseSuccessful = pauseInternal(requestCoordinator, pauseDuration, maxPauseWaitTime);
    } catch (Exception e) {
      pauseFailedException = new PauseFailedException("Pause operation failed.", e);
    }

    // Run unpause operation.
    try {
      unpauseSuccessful =
          unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT, targetBeforePause);
    } catch (Exception e) {
      unpauseFailedException = new UnpauseFailedException("Unpause operation failed.", e);
    }

    // Get pods and deployment information after pause.
    TargetSnapshot targetAfterPause;
    try {
      targetAfterPause = getTarget();
    } catch (Exception e) {
      throw new PauserException(
          "Failed to find the target pods to examine if the targets pods were updated during"
              + " paused.",
          e);
    }

    // Check if pods and deployment information are the same between before pause and after pause.
    try {
      compareTargetSuccessful = compareTargetStates(targetBeforePause, targetAfterPause);
    } catch (Exception e) {
      statusCheckFailedException = new StatusCheckFailedException("Status check failed.", e);
    }

    // If both the pause operation and status check succeeded, you can use the backup that was taken
    // during the pause duration.
    boolean backupOk = pauseSuccessful && compareTargetSuccessful;

    // Return the final result based on each process.
    if (backupOk) { // Backup OK
      if (unpauseSuccessful) { // Backup OK and Unpause OK
        return new PausedDuration(startTime, endTime);
      } else { // Backup OK but Unpause NG
        String errorMessage =
            String.format(
                "Unpause operation failed. Scalar products might still be in a paused state. You"
                    + " must restart related pods by using the `kubectl rollout restart deployment"
                    + " %s` command to unpause all pods. However, the pause operations for taking"
                    + " backup succeeded. You can use a backup that was taken during this pause"
                    + " duration: Start Time = %s, End Time = %s.",
                Objects.requireNonNull(targetBeforePause.getDeployment().getMetadata()).getName(),
                startTime,
                endTime);
        // Users who directly utilize this library, bypassing our CLI, are responsible for proper
        // exception handling. However, this scenario represents a critical issue. Consequently,
        // we output the error message here regardless of whether the calling code handles the
        // exception.
        logger.error(errorMessage);
        throw new UnpauseFailedException(errorMessage, unpauseFailedException);
      }
    } else { // Backup NG
      if (unpauseSuccessful) { // Backup NG but Unpause OK
        if (!pauseSuccessful) { // Backup NG (Pause operation failed) but Unpause OK
          String errorMessage =
              String.format(
                  "Pause operation failed. You cannot use the backup that was taken during this"
                      + " pause duration. You need to retry the pause operation from the beginning"
                      + " to take a backup.");
          throw new PauseFailedException(errorMessage, pauseFailedException);
        } else { // Backup NG (Status check failed) but Unpause OK
          String errorMessage =
              String.format(
                  "Status check failed. You cannot use the backup that was taken during this pause"
                      + " duration. You need to retry the pause operation from the beginning to"
                      + " take a backup.");
          throw new StatusCheckFailedException(errorMessage, statusCheckFailedException);
        }
      } else { // Backup NG and Unpause NG
        String errorMessage =
            String.format(
                "Pause and unpause operation failed. Scalar products might still be in a paused"
                    + " state. You must restart related pods by using the `kubectl rollout restart"
                    + " deployment %s` command to unpause all pods.",
                Objects.requireNonNull(targetBeforePause.getDeployment().getMetadata()).getName());
        // Users who directly utilize this library, bypassing our CLI, are responsible for proper
        // exception handling. However, this scenario represents a critical issue. Consequently,
        // we output the error message here regardless of whether the calling code handles the
        // exception.
        logger.error(errorMessage);
        throw new UnpauseFailedException(errorMessage, unpauseFailedException);
      }
    }
  }

  @VisibleForTesting
  boolean unpauseWithRetry(RequestCoordinator coordinator, int maxRetryCount, TargetSnapshot target)
      throws PauserException {
    int retryCounter = 0;

    while (true) {
      try {
        coordinator.unpause();
        return true;
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

  private boolean pauseInternal(
      RequestCoordinator requestCoordinator, int pauseDuration, @Nullable Long maxPauseWaitTime) {

    requestCoordinator.pause(true, maxPauseWaitTime);
    startTime = Instant.now();
    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS);
    endTime = Instant.now();

    return true;
  }

  private boolean compareTargetStates(TargetSnapshot before, TargetSnapshot after) {
    return before.getStatus().equals(after.getStatus());
  }
}
