package com.scalar.admin.kubernetes;

import static com.scalar.admin.kubernetes.Pauser.MAX_UNPAUSE_RETRY_COUNT;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PauserTest {

  private MockedStatic<Config> mockedConfig;
  private RequestCoordinator requestCoordinator;
  private TargetSnapshot targetBeforePause;
  private TargetSnapshot targetAfterPause;
  private V1Deployment deployment;
  private V1ObjectMeta objectMeta;
  private static final String DUMMY_OBJECT_NAME = "dummyObjectName";

  @BeforeEach
  void beforeEach() throws PauserException {
    mockedConfig = mockStatic(Config.class);
    mockedConfig.when(() -> Config.defaultClient()).thenReturn(null);
    requestCoordinator = mock(RequestCoordinator.class);
    deployment = mock(V1Deployment.class);
    objectMeta = mock(V1ObjectMeta.class);
    targetBeforePause = mock(TargetSnapshot.class);
    targetAfterPause = mock(TargetSnapshot.class);

    doReturn(deployment).when(targetBeforePause).getDeployment();
    doReturn(deployment).when(targetAfterPause).getDeployment();
    doReturn(objectMeta).when(deployment).getMetadata();
    doReturn(DUMMY_OBJECT_NAME).when(objectMeta).getName();
  }

  @AfterEach
  void afterEach() {
    mockedConfig.close();
  }

  @Nested
  class Constructor {
    @Test
    void constructor_WithCorrectArgs_ReturnPauser() throws PauserException, IOException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";

      // Act & Assert
      assertDoesNotThrow(() -> new Pauser(namespace, helmReleaseName));
    }

    @Test
    void constructor_WithNullNamespace_ShouldThrowIllegalArgumentException() {
      // Arrange
      String namespace = null;
      String helmReleaseName = "dummyRelease";

      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class, () -> new Pauser(namespace, helmReleaseName));
      assertEquals("namespace is required", thrown.getMessage());
    }

    @Test
    void constructor_WithNullHelmReleaseName_ShouldThrowIllegalArgumentException() {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = null;

      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class, () -> new Pauser(namespace, helmReleaseName));
      assertEquals("helmReleaseName is required", thrown.getMessage());
    }

    @Test
    void constructor_WhenSetDefaultApiClientThrowIOException_ShouldThrowPauserException() {
      // Arrange
      mockedConfig.when(() -> Config.defaultClient()).thenThrow(IOException.class);
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> new Pauser(namespace, helmReleaseName));
      assertEquals("Failed to set default Kubernetes client.", thrown.getMessage());
    }
  }

  @Nested
  class Pause {

    @Test
    void pause_WhenPauseSucceeded_ReturnPausedDuration() throws PauserException {
      Map<String, Integer> podRestartCounts =
          new HashMap<String, Integer>() {
            {
              put("dummyKey", 1);
            }
          };
      Map<String, String> podResourceVersions =
          new HashMap<String, String>() {
            {
              put("dummyKey", "dummyValue");
            }
          };
      TargetStatus beforeTargetStatus =
          new TargetStatus(podRestartCounts, podResourceVersions, "sameValue");
      TargetStatus afterTargetStatus =
          new TargetStatus(podRestartCounts, podResourceVersions, "sameValue");
      doReturn(beforeTargetStatus).when(targetBeforePause).getStatus();
      doReturn(afterTargetStatus).when(targetAfterPause).getStatus();

      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);
      MockedStatic<Instant> mockedTime = mockStatic(Instant.class);
      mockedTime.when(() -> Instant.now()).thenReturn(startTime).thenReturn(endTime);

      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(requestCoordinator).pause(true, null);
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());

      // Act & Assert
      PausedDuration actual = assertDoesNotThrow(() -> pauser.pause(pauseDuration, 3000L));
      PausedDuration expected = new PausedDuration(startTime, endTime);
      assertEquals(actual.getStartTime(), expected.getStartTime());
      assertEquals(actual.getEndTime(), expected.getEndTime());

      mockedTime.close();
    }

    @Test
    void pause_LessThanOnePauseDuration_ShouldThrowIllegalArgumentException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 0;
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "pauseDuration is required to be greater than 0 millisecond.", thrown.getMessage());
    }

    @Test
    void pause_WhenFirstGetTargetThrowException_ShouldThrowPauserException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doThrow(RuntimeException.class).when(pauser).getTarget();

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals("Failed to find the target pods to pause.", thrown.getMessage());
    }

    @Test
    void pause_WhenGetRequestCoordinatorThrowException_ShouldThrowPauserException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).when(pauser).getTarget();
      doThrow(RuntimeException.class).when(pauser).getRequestCoordinator(targetBeforePause);

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals("Failed to initialize the request coordinator.", thrown.getMessage());
    }

    @Test
    void pause_WhenPauseInternalThrowException_ShouldThrowPauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doThrow(RuntimeException.class)
          .when(pauser)
          .pauseInternal(requestCoordinator, pauseDuration, null);
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());
      doReturn(true).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Pause operation failed. You cannot use the backup that was taken during this pause"
              + " duration. You need to retry the pause operation from the beginning to take a"
              + " backup. ",
          thrown.getMessage());
    }

    @Test
    void pause_WhenRequestCoordinatorPauseThrowException_ShouldThrowPauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doThrow(RuntimeException.class).when(requestCoordinator).pause(true, null);
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());
      doReturn(true).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Pause operation failed. You cannot use the backup that was taken during this pause"
              + " duration. You need to retry the pause operation from the beginning to take a"
              + " backup. ",
          thrown.getMessage());
    }

    @Test
    void pause_WhenInstantNowThrowException_ShouldThrowPauseFailedException()
        throws PauserException {
      // Arrange
      MockedStatic<Instant> mockedTime = mockStatic(Instant.class);
      mockedTime.when(() -> Instant.now()).thenThrow(RuntimeException.class);
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());
      doReturn(true).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Pause operation failed. You cannot use the backup that was taken during this pause"
              + " duration. You need to retry the pause operation from the beginning to take a"
              + " backup. ",
          thrown.getMessage());

      mockedTime.close();
    }

    @Test
    void pause_WhenSleepThrowException_ShouldThrowPauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      MockedStatic<Uninterruptibles> mockedSleep = mockStatic(Uninterruptibles.class);
      mockedSleep
          .when(() -> Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS))
          .thenThrow(RuntimeException.class);
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());
      doReturn(true).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Pause operation failed. You cannot use the backup that was taken during this pause"
              + " duration. You need to retry the pause operation from the beginning to take a"
              + " backup. ",
          thrown.getMessage());

      mockedSleep.close();
    }

    @Test
    void pause_WhenUnpauseWithRetryThrowException_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;

      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);
      MockedStatic<Instant> mockedTime = mockStatic(Instant.class);
      mockedTime.when(() -> Instant.now()).thenReturn(startTime).thenReturn(endTime);

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(true).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          String.format(
              "Unpause operation failed. Scalar products might still be in a paused state. You must"
                  + " restart related pods by using the `kubectl rollout restart deployment %s`"
                  + " command to unpause all pods. Note that the pause operations for taking backup"
                  + " succeeded. You can use a backup that was taken during this pause duration:"
                  + " Start Time = %s, End Time = %s. ",
              DUMMY_OBJECT_NAME, startTime, endTime),
          thrown.getMessage());

      mockedTime.close();
    }

    @Test
    void pause_WhenSecondGetTargetThrowException_ShouldThrowPauserException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doThrow(RuntimeException.class).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Failed to find the target pods to examine if the targets pods were updated during"
              + " paused. ",
          thrown.getMessage());
    }

    @Test
    void
        pause_WhenSecondGetTargetAndUnpauseWithRetryThrowException_ShouldThrowUnpauseFailedException()
            throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doThrow(RuntimeException.class).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          String.format(
              "Unpause operation failed. Scalar products might still be in a paused state. You"
                  + " must restart related pods by using the `kubectl rollout restart deployment"
                  + " %s` command to unpause all pods. ",
              DUMMY_OBJECT_NAME),
          thrown.getMessage());
    }

    @Test
    void pause_WhenCompareTargetStatusThrowException_ShouldThrowStatusCheckFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());
      doThrow(RuntimeException.class).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      StatusCheckFailedException thrown =
          assertThrows(StatusCheckFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Status check failed. You cannot use the backup that was taken during this pause"
              + " duration. You need to retry the pause operation from the beginning to take a"
              + " backup. ",
          thrown.getMessage());
    }

    @Test
    void
        pause_WhenCompareTargetStatusAndUnpauseWithRetryThrowException_ShouldThrowUnpauseFailedException()
            throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doThrow(RuntimeException.class).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          String.format(
              "Unpause operation failed. Scalar products might still be in a paused state. You must"
                  + " restart related pods by using the `kubectl rollout restart deployment"
                  + " %s` command to unpause all pods. ",
              DUMMY_OBJECT_NAME),
          thrown.getMessage());
    }

    @Test
    void pause_WhenTargetPodStatusChanged_ShouldThrowStatusCheckFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);

      Map<String, Integer> podRestartCounts =
          new HashMap<String, Integer>() {
            {
              put("dummyKey", 1);
            }
          };
      Map<String, String> podResourceVersions =
          new HashMap<String, String>() {
            {
              put("dummyKey", "dummyValue");
            }
          };
      TargetStatus beforeTargetStatus =
          new TargetStatus(podRestartCounts, podResourceVersions, "beforeDifferentValue");
      TargetStatus afterTargetStatus =
          new TargetStatus(podRestartCounts, podResourceVersions, "afterDifferentValue");
      doReturn(beforeTargetStatus).when(targetBeforePause).getStatus();
      doReturn(afterTargetStatus).when(targetAfterPause).getStatus();

      doNothing().when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "The target pods were updated during the pause duration. You cannot use the backup that"
              + " was taken during this pause duration. ",
          thrown.getMessage());
    }

    @Test
    void pause_WhenPauseAndUnpauseFailed_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doThrow(RuntimeException.class)
          .when(pauser)
          .pauseInternal(requestCoordinator, pauseDuration, null);
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(true).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          String.format(
              "Unpause operation failed. Scalar products might still be in a paused state. You"
                  + " must restart related pods by using the `kubectl rollout restart deployment"
                  + " %s` command to unpause all pods. Pause operation failed. You cannot use the"
                  + " backup that was taken during this pause"
                  + " duration. You need to retry the pause operation from the beginning to"
                  + " take a backup. ",
              DUMMY_OBJECT_NAME),
          thrown.getMessage());
    }

    @Test
    void pause_WhenUnpauseFailedAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doNothing().when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(false).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          String.format(
              "Unpause operation failed. Scalar products might still be in a paused state. You must"
                  + " restart related pods by using the `kubectl rollout restart deployment %s`"
                  + " command to unpause all pods. The target pods were updated during the pause"
                  + " duration. You cannot use the backup that was taken during this pause"
                  + " duration. ",
              DUMMY_OBJECT_NAME),
          thrown.getMessage());
    }

    @Test
    void pause_WhenPauseAndUnpauseAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doThrow(RuntimeException.class)
          .when(pauser)
          .pauseInternal(requestCoordinator, pauseDuration, null);
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(false).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          String.format(
              "Unpause operation failed. Scalar products might still be in a paused state. You"
                  + " must restart related pods by using the `kubectl rollout restart deployment"
                  + " %s` command to unpause all pods. Pause operation failed. You cannot use the"
                  + " backup that was taken during this pause duration. You need to retry the pause"
                  + " operation from the beginning to take a backup. The target pods were updated"
                  + " during the pause duration. You cannot use the backup that was taken during"
                  + " this pause duration. ",
              DUMMY_OBJECT_NAME),
          thrown.getMessage());
    }

    @Test
    void pause_WhenPauseFailedAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException()
        throws PauserException { // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doThrow(RuntimeException.class)
          .when(pauser)
          .pauseInternal(requestCoordinator, pauseDuration, null);
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());
      doReturn(false).when(pauser).compareTargetStatus(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Pause operation failed. You cannot use the backup that was taken during this pause"
              + " duration. You need to retry the pause operation from the beginning to take a"
              + " backup. The target pods were updated during the pause duration. You cannot use"
              + " the backup that was taken during this pause duration. ",
          thrown.getMessage());
    }
  }

  @Nested
  class UnpauseWithRetry {
    @Test
    void unpauseWithRetry_WhenUnpauseSucceeded_ReturnWithoutException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      assertDoesNotThrow(
          () -> pauser.unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT));
    }

    @Test
    void unpauseWithRetry_WhenExceptionOccur_ShouldRetryThreeTimes() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);
      doThrow(RuntimeException.class).when(requestCoordinator).unpause();

      // Act & Assert
      RuntimeException thrown =
          assertThrows(
              RuntimeException.class,
              () -> pauser.unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT));
      verify(requestCoordinator, times(MAX_UNPAUSE_RETRY_COUNT)).unpause();
    }
  }
}
