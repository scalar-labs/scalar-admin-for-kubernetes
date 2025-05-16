package com.scalar.admin.kubernetes;

import static com.scalar.admin.kubernetes.Pauser.*;
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
      doReturn(null).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());
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
      doReturn(null).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());
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
      doReturn(null).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());

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
      doReturn(null).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());

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

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      PausedDuration pausedDuration = new PausedDuration(startTime, endTime);

      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doReturn(pausedDuration).when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(null).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenSecondGetTargetThrowException_ShouldThrowGetTargetAfterPauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      PausedDuration pausedDuration = new PausedDuration(startTime, endTime);

      doReturn(targetBeforePause).doThrow(RuntimeException.class).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doReturn(pausedDuration).when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());

      // Act & Assert
      GetTargetAfterPauseFailedException thrown =
          assertThrows(
              GetTargetAfterPauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(GET_TARGET_AFTER_PAUSE_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenTargetStatusEqualsThrowException_ShouldThrowStatusCheckFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      PausedDuration pausedDuration = new PausedDuration(startTime, endTime);

      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doReturn(pausedDuration).when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());
      doThrow(RuntimeException.class).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      StatusCheckFailedException thrown =
          assertThrows(StatusCheckFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(STATUS_CHECK_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenTargetPodStatusChanged_ShouldThrowStatusUnmatchedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      PausedDuration pausedDuration = new PausedDuration(startTime, endTime);

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

      doReturn(pausedDuration).when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(pauser).unpauseWithRetry(any(), anyInt());

      // Act & Assert
      StatusUnmatchedException thrown =
          assertThrows(StatusUnmatchedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(STATUS_UNMATCHED_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenPauseAndUnpauseThrowException_ShouldThrowUnpauseFailedException()
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
      doReturn(null).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(PauseFailedException.class, thrown.getSuppressed()[0].getClass());
    }

    @Test
    void
        pause_WhenSecondGetTargetAndUnpauseWithRetryThrowException_ShouldThrowUnpauseFailedException()
            throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      PausedDuration pausedDuration = new PausedDuration(startTime, endTime);

      doReturn(targetBeforePause).doThrow(RuntimeException.class).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doReturn(pausedDuration).when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(GetTargetAfterPauseFailedException.class, thrown.getSuppressed()[0].getClass());
    }

    @Test
    void
        pause_WhenTargetStatusEqualsAndUnpauseWithRetryThrowException_ShouldThrowUnpauseFailedException()
            throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      PausedDuration pausedDuration = new PausedDuration(startTime, endTime);

      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doReturn(pausedDuration).when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doThrow(RuntimeException.class).when(pauser).targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(StatusCheckFailedException.class, thrown.getSuppressed()[0].getClass());
    }

    @Test
    void pause_WhenUnpauseFailedAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      PausedDuration pausedDuration = new PausedDuration(startTime, endTime);

      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetBeforePause);
      doReturn(pausedDuration).when(pauser).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(new StatusUnmatchedException(STATUS_UNMATCHED_ERROR_MESSAGE))
          .when(pauser)
          .targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(StatusUnmatchedException.class, thrown.getSuppressed()[0].getClass());
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
      doReturn(new StatusUnmatchedException(STATUS_UNMATCHED_ERROR_MESSAGE))
          .when(pauser)
          .targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(UnpauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenPauseFailedAndTargetPodStatusChanged_ShouldThrowPauseFailedException()
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
      doReturn(new StatusUnmatchedException(STATUS_UNMATCHED_ERROR_MESSAGE))
          .when(pauser)
          .targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(PauseFailedException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());
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

  @Nested
  class buildException {
    @Test
    void buildException_00000_ReturnNull() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertNull(actual);
    }

    @Test
    void buildException_00001_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(StatusUnmatchedException.class, actual.getClass());
    }

    @Test
    void buildException_00010_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(StatusCheckFailedException.class, actual.getClass());
    }

    @Test
    void buildException_00011_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(StatusCheckFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_00100_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_00110_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_00101_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_00111_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01000_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_01001_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_01010_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";

      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";
      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_01011_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01100_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_01101_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01110_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01111_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_10000_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";
      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_10001_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_10010_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_10011_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_10100_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_10101_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_10110_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_10111_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11000_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_11001_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_11010_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_11011_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11100_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_11101_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11110_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11111_ThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);

      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          pauser.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[2].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[3].getClass());
    }
  }
}
