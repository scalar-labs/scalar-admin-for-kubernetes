package com.scalar.admin.kubernetes.domain.service;

import static com.scalar.admin.kubernetes.domain.service.PauseService.GET_TARGET_AFTER_PAUSE_ERROR_MESSAGE;
import static com.scalar.admin.kubernetes.domain.service.PauseService.MAX_UNPAUSE_RETRY_COUNT;
import static com.scalar.admin.kubernetes.domain.service.PauseService.PAUSE_ERROR_MESSAGE;
import static com.scalar.admin.kubernetes.domain.service.PauseService.STATUS_CHECK_ERROR_MESSAGE;
import static com.scalar.admin.kubernetes.domain.service.PauseService.STATUS_UNMATCHED_ERROR_MESSAGE;
import static com.scalar.admin.kubernetes.domain.service.PauseService.UNPAUSE_ERROR_MESSAGE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PauseServiceTest {

  private ScalarAdminClient client;
  private PauseTarget targetBeforePause;
  private PauseTarget targetAfterPause;

  @BeforeEach
  void beforeEach() {
    client = mock(ScalarAdminClient.class);
    targetBeforePause = mock(PauseTarget.class);
    targetAfterPause = mock(PauseTarget.class);
  }

  @Nested
  class Pause {

    @Test
    void pause_WhenPauseSucceeded_ReturnPauseDuration() throws PauserException {
      // Arrange
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
      PauseTarget.Status beforeTargetStatus =
          new PauseTarget.Status(podRestartCounts, podResourceVersions, "sameValue");
      PauseTarget.Status afterTargetStatus =
          new PauseTarget.Status(podRestartCounts, podResourceVersions, "sameValue");
      doReturn(beforeTargetStatus).when(targetBeforePause).toStatus();
      doReturn(afterTargetStatus).when(targetAfterPause).toStatus();

      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);
      MockedStatic<Instant> mockedTime = mockStatic(Instant.class);
      mockedTime.when(() -> Instant.now()).thenReturn(startTime).thenReturn(endTime);

      int pauseDuration = 1;
      PauseService service = spy(new PauseService());
      doNothing().when(client).pause(true, null);
      doNothing().when(service).unpauseWithRetry(any(), anyInt());

      // Act
      PauseDuration actual =
          assertDoesNotThrow(
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, 3000L));

      // Assert
      PauseDuration expected = new PauseDuration(startTime, endTime);
      assertEquals(actual.startTime(), expected.startTime());
      assertEquals(actual.endTime(), expected.endTime());

      mockedTime.close();
    }

    @Test
    void pause_NullTargetBeforePause_ShouldThrowNullPointerException() {
      // Arrange
      PauseService service = new PauseService();

      // Act & Assert
      NullPointerException thrown =
          assertThrows(
              NullPointerException.class,
              () -> service.pause(null, () -> targetAfterPause, client, 1000, null));
      assertEquals("targetBeforePause is required", thrown.getMessage());
    }

    @Test
    void pause_NullTargetAfterPauseSupplier_ShouldThrowNullPointerException() {
      // Arrange
      PauseService service = new PauseService();

      // Act & Assert
      NullPointerException thrown =
          assertThrows(
              NullPointerException.class,
              () -> service.pause(targetBeforePause, null, client, 1000, null));
      assertEquals("targetAfterPauseSupplier is required", thrown.getMessage());
    }

    @Test
    void pause_NullClient_ShouldThrowNullPointerException() {
      // Arrange
      PauseService service = new PauseService();

      // Act & Assert
      NullPointerException thrown =
          assertThrows(
              NullPointerException.class,
              () -> service.pause(targetBeforePause, () -> targetAfterPause, null, 1000, null));
      assertEquals("client is required", thrown.getMessage());
    }

    @Test
    void pause_LessThanOnePauseDuration_ShouldThrowIllegalArgumentException() {
      // Arrange
      int pauseDuration = 0;
      PauseService service = new PauseService();

      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(
          "pauseDuration is required to be greater than 0 millisecond.", thrown.getMessage());
    }

    @Test
    void pause_WhenClientPauseThrowException_ShouldThrowPauseFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      PauseService service = spy(new PauseService());
      doThrow(RuntimeException.class).when(client).pause(true, null);
      doNothing().when(service).unpauseWithRetry(any(), anyInt());
      doReturn(null).when(service).targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(
              PauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenInstantNowThrowException_ShouldThrowPauseFailedException()
        throws PauserException {
      // Arrange
      MockedStatic<Instant> mockedTime = mockStatic(Instant.class);
      mockedTime.when(() -> Instant.now()).thenThrow(RuntimeException.class);
      int pauseDuration = 1;
      PauseService service = spy(new PauseService());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());
      doReturn(null).when(service).targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(
              PauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
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
      PauseService service = spy(new PauseService());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());
      doReturn(null).when(service).targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(
              PauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());

      mockedSleep.close();
    }

    @Test
    void pause_WhenUnpauseWithRetryThrowException_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      PauseService service = spy(new PauseService());
      PauseDuration pausedDuration = new PauseDuration(startTime, endTime);

      doReturn(pausedDuration).when(service).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(service)
          .unpauseWithRetry(client, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(null).when(service).targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(
              UnpauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenGetTargetAfterPauseThrowException_ShouldThrowGetTargetAfterPauseFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      PauseService service = spy(new PauseService());
      PauseDuration pausedDuration = new PauseDuration(startTime, endTime);

      doReturn(pausedDuration).when(service).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());

      PauseService.PauseTargetSupplier supplier =
          () -> {
            throw new RuntimeException();
          };

      // Act & Assert
      GetTargetAfterPauseFailedException thrown =
          assertThrows(
              GetTargetAfterPauseFailedException.class,
              () -> service.pause(targetBeforePause, supplier, client, pauseDuration, null));
      assertEquals(GET_TARGET_AFTER_PAUSE_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenTargetStatusEqualsThrowException_ShouldThrowStatusCheckFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      PauseService service = spy(new PauseService());
      PauseDuration pausedDuration = new PauseDuration(startTime, endTime);

      doReturn(pausedDuration).when(service).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());
      doThrow(RuntimeException.class).when(service).targetStatusEquals(any(), any());

      // Act & Assert
      StatusCheckFailedException thrown =
          assertThrows(
              StatusCheckFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(STATUS_CHECK_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenDeploymentResourceVersionChanges_ShouldThrowStatusUnmatchedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      PauseService service = spy(new PauseService());
      PauseDuration pausedDuration = new PauseDuration(startTime, endTime);

      doReturn(pausedDuration).when(service).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());

      Map<String, Integer> podRestartCounts =
          new HashMap<String, Integer>() {
            {
              put("pod-1", 0);
            }
          };
      Map<String, String> podResourceVersions =
          new HashMap<String, String>() {
            {
              put("pod-1", "12345");
            }
          };
      PauseTarget.Status beforeTargetStatus =
          new PauseTarget.Status(podRestartCounts, podResourceVersions, "beforeVersion");
      PauseTarget.Status afterTargetStatus =
          new PauseTarget.Status(podRestartCounts, podResourceVersions, "afterVersion");
      doReturn(beforeTargetStatus).when(targetBeforePause).toStatus();
      doReturn(afterTargetStatus).when(targetAfterPause).toStatus();

      // Act & Assert
      StatusUnmatchedException thrown =
          assertThrows(
              StatusUnmatchedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(STATUS_UNMATCHED_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenPodNameChanges_ShouldThrowStatusUnmatchedException()
        throws PauserException {
      // Arrange
      PauseService service = spy(new PauseService());
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);
      PauseDuration pausedDuration = new PauseDuration(startTime, endTime);

      doReturn(pausedDuration).when(service).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());

      Map<String, Integer> beforePodRestartCounts =
          new HashMap<String, Integer>() {
            {
              put("pod-1", 0);
              put("pod-2", 0);
              put("pod-3", 0);
            }
          };
      Map<String, Integer> afterPodRestartCounts =
          new HashMap<String, Integer>() {
            {
              put("pod-1", 0);
              put("pod-2", 0);
              put("pod-4", 0); // pod-3 deleted and pod-4 created (name changed)
            }
          };
      Map<String, String> beforePodResourceVersions =
          new HashMap<String, String>() {
            {
              put("pod-1", "12345");
              put("pod-2", "12346");
              put("pod-3", "12347");
            }
          };
      Map<String, String> afterPodResourceVersions =
          new HashMap<String, String>() {
            {
              put("pod-1", "12345");
              put("pod-2", "12346");
              put("pod-4", "12348"); // new pod has new resource version
            }
          };
      PauseTarget.Status beforeTargetStatus =
          new PauseTarget.Status(beforePodRestartCounts, beforePodResourceVersions, "sameVersion");
      PauseTarget.Status afterTargetStatus =
          new PauseTarget.Status(afterPodRestartCounts, afterPodResourceVersions, "sameVersion");
      doReturn(beforeTargetStatus).when(targetBeforePause).toStatus();
      doReturn(afterTargetStatus).when(targetAfterPause).toStatus();

      int pauseDuration = 1;

      // Act & Assert
      StatusUnmatchedException thrown =
          assertThrows(
              StatusUnmatchedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(STATUS_UNMATCHED_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenPodRestartCountChanges_ShouldThrowStatusUnmatchedException()
        throws PauserException {
      // Arrange
      PauseService service = spy(new PauseService());
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);
      PauseDuration pausedDuration = new PauseDuration(startTime, endTime);

      doReturn(pausedDuration).when(service).pauseInternal(any(), anyInt(), anyLong());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());

      Map<String, Integer> beforePodRestartCounts =
          new HashMap<String, Integer>() {
            {
              put("pod-1", 0);
              put("pod-2", 0);
              put("pod-3", 0);
            }
          };
      Map<String, Integer> afterPodRestartCounts =
          new HashMap<String, Integer>() {
            {
              put("pod-1", 0);
              put("pod-2", 1); // restart count changed for one pod
              put("pod-3", 0);
            }
          };
      Map<String, String> beforePodResourceVersions =
          new HashMap<String, String>() {
            {
              put("pod-1", "12345");
              put("pod-2", "12346");
              put("pod-3", "12347");
            }
          };
      Map<String, String> afterPodResourceVersions =
          new HashMap<String, String>() {
            {
              put("pod-1", "12345");
              put("pod-2", "12350"); // resource version also changed when container restarted
              put("pod-3", "12347");
            }
          };
      PauseTarget.Status beforeTargetStatus =
          new PauseTarget.Status(beforePodRestartCounts, beforePodResourceVersions, "sameValue");
      PauseTarget.Status afterTargetStatus =
          new PauseTarget.Status(afterPodRestartCounts, afterPodResourceVersions, "sameValue");
      doReturn(beforeTargetStatus).when(targetBeforePause).toStatus();
      doReturn(afterTargetStatus).when(targetAfterPause).toStatus();

      int pauseDuration = 1;

      // Act & Assert
      StatusUnmatchedException thrown =
          assertThrows(
              StatusUnmatchedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(STATUS_UNMATCHED_ERROR_MESSAGE, thrown.getMessage());
    }

    @Test
    void pause_WhenPauseAndUnpauseThrowException_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      PauseService service = spy(new PauseService());
      doThrow(RuntimeException.class).when(service).pauseInternal(any(), anyInt(), any());
      doThrow(RuntimeException.class)
          .when(service)
          .unpauseWithRetry(client, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(null).when(service).targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(
              UnpauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(PauseFailedException.class, thrown.getSuppressed()[0].getClass());
    }

    @Test
    void pause_WhenUnpauseFailedAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      Instant startTime = Instant.now().minus(5, SECONDS);
      Instant endTime = Instant.now().plus(5, SECONDS);

      PauseService service = spy(new PauseService());
      PauseDuration pausedDuration = new PauseDuration(startTime, endTime);

      doReturn(pausedDuration).when(service).pauseInternal(any(), anyInt(), anyLong());
      doThrow(RuntimeException.class)
          .when(service)
          .unpauseWithRetry(client, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(new StatusUnmatchedException(STATUS_UNMATCHED_ERROR_MESSAGE))
          .when(service)
          .targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(
              UnpauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(StatusUnmatchedException.class, thrown.getSuppressed()[0].getClass());
    }

    @Test
    void pause_WhenPauseAndUnpauseAndTargetPodStatusChanged_ShouldThrowUnpauseFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      PauseService service = spy(new PauseService());
      doThrow(RuntimeException.class).when(service).pauseInternal(any(), anyInt(), any());
      doThrow(RuntimeException.class)
          .when(service)
          .unpauseWithRetry(client, MAX_UNPAUSE_RETRY_COUNT);
      doReturn(new StatusUnmatchedException(STATUS_UNMATCHED_ERROR_MESSAGE))
          .when(service)
          .targetStatusEquals(any(), any());

      // Act & Assert
      UnpauseFailedException thrown =
          assertThrows(
              UnpauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(UNPAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(PauseFailedException.class, thrown.getSuppressed()[0].getClass());
      assertEquals(StatusUnmatchedException.class, thrown.getSuppressed()[1].getClass());
    }

    @Test
    void pause_WhenPauseFailedAndTargetPodStatusChanged_ShouldThrowPauseFailedException()
        throws PauserException {
      // Arrange
      int pauseDuration = 1;
      PauseService service = spy(new PauseService());
      doThrow(RuntimeException.class).when(service).pauseInternal(any(), anyInt(), any());
      doNothing().when(service).unpauseWithRetry(any(), anyInt());
      doReturn(new StatusUnmatchedException(STATUS_UNMATCHED_ERROR_MESSAGE))
          .when(service)
          .targetStatusEquals(any(), any());

      // Act & Assert
      PauseFailedException thrown =
          assertThrows(
              PauseFailedException.class,
              () ->
                  service.pause(
                      targetBeforePause, () -> targetAfterPause, client, pauseDuration, null));
      assertEquals(PAUSE_ERROR_MESSAGE, thrown.getMessage());
      assertEquals(StatusUnmatchedException.class, thrown.getSuppressed()[0].getClass());
    }
  }

  @Nested
  class UnpauseWithRetry {
    @Test
    void unpauseWithRetry_WhenUnpauseSucceeded_ReturnWithoutException() {
      // Arrange
      PauseService service = new PauseService();
      doNothing().when(client).unpause();

      // Act & Assert
      assertDoesNotThrow(() -> service.unpauseWithRetry(client, MAX_UNPAUSE_RETRY_COUNT));
    }

    @Test
    void unpauseWithRetry_WhenExceptionOccur_ShouldRetryThreeTimes() {
      // Arrange
      PauseService service = new PauseService();
      doThrow(RuntimeException.class).when(client).unpause();

      // Act & Assert
      RuntimeException thrown =
          assertThrows(
              RuntimeException.class,
              () -> service.unpauseWithRetry(client, MAX_UNPAUSE_RETRY_COUNT));
      verify(client, times(MAX_UNPAUSE_RETRY_COUNT)).unpause();
    }
  }

  @Nested
  class BuildException {
    @Test
    void buildException_00000_ReturnNull() {
      // Arrange
      PauseService service = new PauseService();

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          service.buildException(
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
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(StatusCheckFailedException.class, actual.getClass());
    }

    @Test
    void buildException_00011_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_00100_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(GetTargetAfterPauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_00101_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_00110_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_00111_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_01000_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(PauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_01001_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_01010_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_01011_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_01100_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = null;
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_01101_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_01110_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_01111_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_10000_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
              unpauseFailedException,
              pauseFailedException,
              getTargetAfterPauseFailedException,
              statusCheckFailedException,
              statusUnmatchedException);

      // Assert
      assertEquals(UnpauseFailedException.class, actual.getClass());
    }

    @Test
    void buildException_10001_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_10010_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_10011_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_10100_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = null;
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_10101_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_10110_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_10111_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_11000_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_11001_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException =
          new StatusUnmatchedException(dummyMessage);

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_11010_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException = null;
      StatusCheckFailedException statusCheckFailedException =
          new StatusCheckFailedException(dummyMessage);
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_11011_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_11100_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
      String dummyMessage = "dummyMessage";

      UnpauseFailedException unpauseFailedException = new UnpauseFailedException(dummyMessage);
      PauseFailedException pauseFailedException = new PauseFailedException(dummyMessage);
      GetTargetAfterPauseFailedException getTargetAfterPauseFailedException =
          new GetTargetAfterPauseFailedException(dummyMessage);
      StatusCheckFailedException statusCheckFailedException = null;
      StatusUnmatchedException statusUnmatchedException = null;

      // Act
      Exception actual =
          service.buildException(
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
    void buildException_11101_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_11110_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
    void buildException_11111_ThrowException() {
      // Arrange
      PauseService service = new PauseService();
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
          service.buildException(
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
