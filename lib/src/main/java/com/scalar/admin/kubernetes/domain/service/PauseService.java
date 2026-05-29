package com.scalar.admin.kubernetes.domain.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.exception.GetTargetAfterPauseFailedException;
import com.scalar.admin.kubernetes.domain.exception.PauseFailedException;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.exception.StatusCheckFailedException;
import com.scalar.admin.kubernetes.domain.exception.StatusUnmatchedException;
import com.scalar.admin.kubernetes.domain.exception.UnpauseFailedException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseDuration;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Domain service for pause operations on Scalar product pods.
 *
 * <p>This service encapsulates the business logic for pausing and unpausing Scalar product pods,
 * including retry logic, error priority handling, and status validation. The pause operation
 * consists of the following steps:
 *
 * <ol>
 *   <li>Pause the target pods using the provided client.
 *   <li>Wait for the specified duration.
 *   <li>Unpause the target pods (with retry).
 *   <li>Validate that the target pods were not updated during the pause operation.
 * </ol>
 *
 * <p>This class is not thread-safe because the pause operation causes side effects in the states of
 * target pods.
 */
@NotThreadSafe
public class PauseService {

  @VisibleForTesting static final int MAX_UNPAUSE_RETRY_COUNT = 3;

  @VisibleForTesting
  static final String UNPAUSE_ERROR_MESSAGE =
      "Unpause operation failed. Scalar products might still be in a paused state. You"
          + " must restart related pods by using the `kubectl rollout restart deployment"
          + " <DEPLOYMENT_NAME>` command to unpause all pods.";

  @VisibleForTesting
  static final String PAUSE_ERROR_MESSAGE =
      "Pause operation failed. You cannot use the backup that was taken during this pause"
          + " duration. You need to retry the pause operation from the beginning to"
          + " take a backup.";

  @VisibleForTesting
  static final String GET_TARGET_AFTER_PAUSE_ERROR_MESSAGE =
      "Failed to find the target pods to examine if the targets pods were updated during"
          + " paused.";

  @VisibleForTesting
  static final String STATUS_CHECK_ERROR_MESSAGE =
      "Status check failed. You cannot use the backup that was taken during this pause"
          + " duration. You need to retry the pause operation from the beginning to"
          + " take a backup.";

  @VisibleForTesting
  static final String STATUS_UNMATCHED_ERROR_MESSAGE =
      "The target pods were updated during the pause duration. You cannot use the backup that"
          + " was taken during this pause duration.";

  /**
   * Executes a pause operation on the target pods.
   *
   * @param targetBeforePause the pause target before the pause operation
   * @param targetAfterPauseSupplier supplier to get the target after the pause operation
   * @param client the Scalar Admin client for pause/unpause operations
   * @param pauseDuration the duration to pause in milliseconds
   * @param maxPauseWaitTime the max wait time (in milliseconds) until Scalar products drain
   *     outstanding requests, null for default
   * @return the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   */
  public PauseDuration pause(
      PauseTarget targetBeforePause,
      PauseTargetSupplier targetAfterPauseSupplier,
      ScalarAdminClient client,
      int pauseDuration,
      @Nullable Long maxPauseWaitTime)
      throws PauserException {
    if (pauseDuration < 1) {
      throw new IllegalArgumentException(
          "pauseDuration is required to be greater than 0 millisecond.");
    }

    // From here, we cannot throw exceptions right after they occur because we need to take care of
    // the unpause operation failure. We will throw the exception after the unpause operation or at
    // the end of this method.

    // Run a pause operation.
    PauseDuration pausedDuration = null;
    PauseFailedException pauseFailedException = null;
    try {
      pausedDuration = pauseInternal(client, pauseDuration, maxPauseWaitTime);
    } catch (Exception e) {
      pauseFailedException = new PauseFailedException(PAUSE_ERROR_MESSAGE, e);
    }

    // Run an unpause operation.
    UnpauseFailedException unpauseFailedException = null;
    try {
      unpauseWithRetry(client, MAX_UNPAUSE_RETRY_COUNT);
    } catch (Exception e) {
      unpauseFailedException = new UnpauseFailedException(UNPAUSE_ERROR_MESSAGE, e);
    }

    // Get pods and deployment information after pause.
    PauseTarget targetAfterPause = null;
    GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
    try {
      targetAfterPause = targetAfterPauseSupplier.get();
    } catch (Exception e) {
      getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(GET_TARGET_AFTER_PAUSE_ERROR_MESSAGE, e);
    }

    // Check if pods and deployment information are the same between before pause and after pause.
    StatusCheckFailedException statusCheckFailedException = null;
    StatusUnmatchedException statusUnmatchedException = null;
    if (targetAfterPause != null) {
      try {
        statusUnmatchedException = targetStatusEquals(targetBeforePause, targetAfterPause);
      } catch (Exception e) {
        statusCheckFailedException = new StatusCheckFailedException(STATUS_CHECK_ERROR_MESSAGE, e);
      }
    }

    // We use the exceptions as conditions instead of using boolean flags like `isPauseOk`, etc. If
    // we use boolean flags, it might cause a bit large number of combinations. For example, if we
    // have three flags, they generate 2^3 = 8 combinations. It also might make the nested if
    // statement or a lot of branches of the switch statement. That's why we don't use status flags
    // for now.
    PauserException pauserException =
        buildException(
            unpauseFailedException,
            pauseFailedException,
            getTargetAfterPauseFailedException,
            statusCheckFailedException,
            statusUnmatchedException);

    // Return the final result based on each process.
    if (pauserException != null) {
      // Some operations failed.
      throw pauserException;
    } else {
      // All operations succeeded.
      return pausedDuration;
    }
  }

  /**
   * Functional interface for supplying PauseTarget after pause operation.
   *
   * <p>This allows the domain service to request the latest target state without depending on the
   * repository directly.
   */
  @FunctionalInterface
  public interface PauseTargetSupplier {
    PauseTarget get() throws PauserException;
  }

  @VisibleForTesting
  void unpauseWithRetry(ScalarAdminClient client, int maxRetryCount) {
    int retryCounter = 0;
    while (true) {
      try {
        client.unpause();
        return;
      } catch (Exception e) {
        if (++retryCounter >= maxRetryCount) {
          throw e;
        }
      }
    }
  }

  @VisibleForTesting
  PauseDuration pauseInternal(
      ScalarAdminClient client, int pauseDuration, @Nullable Long maxPauseWaitTime) {
    client.pause(true, maxPauseWaitTime);
    Instant startTime = Instant.now();
    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS);
    Instant endTime = Instant.now();
    return new PauseDuration(startTime, endTime);
  }

  @VisibleForTesting
  @Nullable
  StatusUnmatchedException targetStatusEquals(PauseTarget before, PauseTarget after) {
    if (before.toStatus().equals(after.toStatus())) {
      return null;
    } else {
      return new StatusUnmatchedException(STATUS_UNMATCHED_ERROR_MESSAGE);
    }
  }

  @Nullable
  @VisibleForTesting
  PauserException buildException(
      @Nullable UnpauseFailedException unpauseFailedException,
      @Nullable PauseFailedException pauseFailedException,
      @Nullable GetTargetAfterPauseFailedException getTargetAfterPauseFailedException,
      @Nullable StatusCheckFailedException statusCheckFailedException,
      @Nullable StatusUnmatchedException statusUnmatchedException) {
    PauserException pauserException = null;

    // Treat the unpause failure as most critical because it might cause system unavailability.
    if (unpauseFailedException != null) {
      pauserException = unpauseFailedException;
    }

    // Treat the pause failure as the second priority because the issue might be caused by a
    // configuration mistake.
    if (pauseFailedException != null) {
      if (pauserException == null) {
        pauserException = pauseFailedException;
      } else {
        pauserException.addSuppressed(pauseFailedException);
      }
    }

    // Treat the getting target failure as the third priority because this issue might be caused by
    // a temporary glitch, for example, network failures.
    if (getTargetAfterPauseFailedException != null) {
      if (pauserException == null) {
        pauserException = getTargetAfterPauseFailedException;
      } else {
        pauserException.addSuppressed(getTargetAfterPauseFailedException);
      }
    }

    // Treat the status checking failure as the third priority because this issue might be caused by
    // a temporary glitch, for example, getting target information by using the Kubernetes API fails
    // after the pause operation.
    if (statusCheckFailedException != null) {
      if (pauserException == null) {
        pauserException = statusCheckFailedException;
      } else {
        pauserException.addSuppressed(statusCheckFailedException);
      }
    }

    // Treat the status unmatched issue as the third priority because this issue might be caused by
    // temporary glitch, for example, pod restarts.
    if (statusUnmatchedException != null) {
      if (pauserException == null) {
        pauserException = statusUnmatchedException;
      } else {
        pauserException.addSuppressed(statusUnmatchedException);
      }
    }

    return pauserException;
  }
}
