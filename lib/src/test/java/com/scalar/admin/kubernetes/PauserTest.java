package com.scalar.admin.kubernetes;

import static com.scalar.admin.kubernetes.Pauser.*;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PauserTest {

  private RequestCoordinator requestCoordinator;
  private TargetSnapshot targetBeforePause;
  private TargetSnapshot targetAfterPause;
  private V1Deployment deployment;
  private V1ObjectMeta objectMeta;
  private TargetSelector targetSelector;
  private static final String DUMMY_OBJECT_NAME = "dummyObjectName";

  @BeforeEach
  void beforeEach() {
    requestCoordinator = mock(RequestCoordinator.class);
    deployment = mock(V1Deployment.class);
    objectMeta = mock(V1ObjectMeta.class);
    targetBeforePause = mock(TargetSnapshot.class);
    targetAfterPause = mock(TargetSnapshot.class);
    targetSelector = mock(TargetSelector.class);

    doReturn(deployment).when(targetBeforePause).getDeployment();
    doReturn(deployment).when(targetAfterPause).getDeployment();
    doReturn(objectMeta).when(deployment).getMetadata();
    doReturn(DUMMY_OBJECT_NAME).when(objectMeta).getName();
  }

  @Nested
  class Constructor {
    @Test
    void constructor_WithCorrectArgs_ReturnPauser() {
      // Act & Assert
      assertDoesNotThrow(() -> new Pauser(targetSelector));
    }

    @Test
    void constructor_WithNullTargetSelector_ShouldThrowIllegalArgumentException() {
      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> new Pauser(null));
      assertEquals("targetSelector is required", thrown.getMessage());
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

      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_LessThanOnePauseDuration_ShouldThrowIllegalArgumentException() throws PauserException {
      // Arrange
      int pauseDuration = 0;
      Pauser pauser = new Pauser(targetSelector);

      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "pauseDuration is required to be greater than 0 millisecond.", thrown.getMessage());
    }

    @Test
    void pause_WhenFirstGetTargetThrowException_ShouldThrowPauserException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
      doThrow(RuntimeException.class).when(pauser).getTarget();

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals("Failed to find the target pods to pause.", thrown.getMessage());
    }

    @Test
    void pause_WhenGetRequestCoordinatorThrowException_ShouldThrowPauserException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
      doReturn(targetBeforePause).when(pauser).getTarget();
      doThrow(RuntimeException.class).when(pauser).getRequestCoordinator(targetBeforePause);

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals("Failed to initialize the request coordinator.", thrown.getMessage());
    }

    @Test
    void pause_WhenPauseInternalThrowException_ShouldThrowPauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenRequestCoordinatorPauseThrowException_ShouldThrowPauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenInstantNowThrowException_ShouldThrowPauseFailedException() throws PauserException {
      // Arrange
      MockedStatic<Instant> mockedTime = mockStatic(Instant.class);
      mockedTime.when(() -> Instant.now()).thenThrow(RuntimeException.class);
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
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
      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenUnpauseWithRetryThrowException_ShouldThrowUnpauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenSecondGetTargetThrowException_ShouldThrowGetTargetAfterPauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenTargetStatusEqualsThrowException_ShouldThrowStatusCheckFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenTargetPodStatusChanged_ShouldThrowStatusUnmatchedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenPauseAndUnpauseThrowException_ShouldThrowUnpauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
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
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(targetSelector));
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
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenUnpauseFailedAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenPauseAndUnpauseAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
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
    void pause_WhenPauseFailedAndTargetPodStatusChanged_ShouldThrowPauseFailedException() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(targetSelector));
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
    void unpauseWithRetry_WhenUnpauseSucceeded_ReturnWithoutException() {
      // Arrange
      Pauser pauser = new Pauser(targetSelector);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      assertDoesNotThrow(
          () -> pauser.unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT));
    }

    @Test
    void unpauseWithRetry_WhenExceptionOccur_ShouldRetryThreeTimes() {
      // Arrange
      Pauser pauser = new Pauser(targetSelector);
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
    void buildException_00000_ReturnNull() {
      // Arrange
      Pauser pauser = new Pauser(targetSelector);

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
    void buildException_00001_ThrowException() {
      // Arrange
      Pauser pauser = new Pauser(targetSelector);

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
    void buildException_00010_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null, null, null, new StatusCheckFailedException(dummyMessage), null);

      assertEquals(StatusCheckFailedException.class, actual.getClass());
    }

    @Test
    void buildException_00011_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              null,
              null,
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(StatusCheckFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_00100_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null, null, new GetTargetAfterPauseFailedException(dummyMessage), null, null);

      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_00110_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              null,
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              null);

      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_00101_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              null,
              new GetTargetAfterPauseFailedException(dummyMessage),
              null,
              new StatusUnmatchedException(dummyMessage));

      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_00111_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              null,
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01000_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(null, new PauseFailedException(dummyMessage), null, null, null);

      assertEquals(PauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_01001_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              new PauseFailedException(dummyMessage),
              null,
              null,
              new StatusUnmatchedException(dummyMessage));

      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_01010_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              new PauseFailedException(dummyMessage),
              null,
              new StatusCheckFailedException(dummyMessage),
              null);

      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_01011_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              new PauseFailedException(dummyMessage),
              null,
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01100_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              null,
              null);

      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_01101_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              null,
              new StatusUnmatchedException(dummyMessage));

      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01110_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              null);

      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_01111_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              null,
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(PauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_10000_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage), null, null, null, null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_10001_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              null,
              null,
              null,
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_10010_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              null,
              null,
              new StatusCheckFailedException(dummyMessage),
              null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_10011_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              null,
              null,
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_10100_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              null,
              new GetTargetAfterPauseFailedException(dummyMessage),
              null,
              null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_10101_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              null,
              new GetTargetAfterPauseFailedException(dummyMessage),
              null,
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_10110_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              null,
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_10111_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              null,
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11000_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              null,
              null,
              null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
    }

    @Test
    void buildException_11001_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              null,
              null,
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_11010_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              null,
              new StatusCheckFailedException(dummyMessage),
              null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_11011_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              null,
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11100_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              null,
              null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
    }

    @Test
    void buildException_11101_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              null,
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11110_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              null);

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[2].getClass());
    }

    @Test
    void buildException_11111_ThrowException() {
      Pauser pauser = new Pauser(targetSelector);
      String dummyMessage = "dummyMessage";

      Exception actual =
          pauser.buildException(
              new UnpauseFailedException(dummyMessage),
              new PauseFailedException(dummyMessage),
              new GetTargetAfterPauseFailedException(dummyMessage),
              new StatusCheckFailedException(dummyMessage),
              new StatusUnmatchedException(dummyMessage));

      assertEquals(UnpauseFailedException.class, actual.getClass());
      assertEquals(PauseFailedException.class, actual.getSuppressed()[0].getClass());
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getSuppressed()[1].getClass());
      assertEquals(StatusCheckFailedException.class, actual.getSuppressed()[2].getClass());
      assertEquals(StatusUnmatchedException.class, actual.getSuppressed()[3].getClass());
    }
  }
}
