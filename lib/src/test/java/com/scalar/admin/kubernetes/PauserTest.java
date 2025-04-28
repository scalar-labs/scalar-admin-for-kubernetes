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
  private TargetSnapshot targetSnapshot;
  private TargetSelector targetSelector;
  private V1Deployment deployment;
  private V1ObjectMeta objectMeta;
  private final String dummyObjectName = "dummyObjectName";

  @BeforeEach
  void beforeEach() throws PauserException {
    mockedConfig = mockStatic(Config.class);
    mockedConfig.when(() -> Config.defaultClient()).thenReturn(null);
    requestCoordinator = mock(RequestCoordinator.class);
    targetSnapshot = mock(TargetSnapshot.class);
    targetSelector = mock(TargetSelector.class);
    deployment = mock(V1Deployment.class);
    objectMeta = mock(V1ObjectMeta.class);

    when(targetSnapshot.getDeployment()).thenReturn(deployment);
    when(deployment.getMetadata()).thenReturn(objectMeta);
    when(objectMeta.getName()).thenReturn(dummyObjectName);
    when(targetSelector.select()).thenReturn(targetSnapshot);
  }

  @AfterEach
  void afterEach() {
    mockedConfig.close();
  }

  @Nested
  class ConstructorPauser {
    @Test
    void WithCorrectArgsReturnPauser() throws PauserException, IOException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";

      // Act & Assert
      assertDoesNotThrow(() -> new Pauser(namespace, helmReleaseName));
    }

    @Test
    void WithNullNamespaceShouldThrowException() {
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
    void WithNullHelmReleaseNameShouldThrowException() {
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
    void WhenSetDefaultApiClientThrowIOExceptionShouldThrowException() {
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
  class MethodPause {
    @Test
    void LessThanOnePauseDurationShouldThrowException() throws PauserException {
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
    void WhenFirstTargetSelectorThrowExceptionShouldThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = new Pauser(namespace, helmReleaseName);
      when(targetSelector.select()).thenThrow(RuntimeException.class);

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals("Failed to find the target pods to pause.", thrown.getMessage());
    }

    @Test
    void WhenGetRequestCoordinatorThrowExceptionShouldThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).when(pauser).getTarget();
      doThrow(RuntimeException.class).when(pauser).getRequestCoordinator(targetSnapshot);

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals("Failed to initialize the coordinator.", thrown.getMessage());
    }

    @Test
    void WhenCoordinatorPauseThrowExceptionShouldRunUnpause() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doThrow(RuntimeException.class).when(requestCoordinator).pause(true, null);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "The pause operation failed for some reason. However, the unpause operation succeeded"
              + " afterward. Currently, the scalar products are running with the unpause status."
              + " You should retry the pause operation to ensure proper backup.",
          thrown.getMessage());
      verify(pauser, times(1))
          .unpauseWithRetry(
              any(RequestCoordinator.class),
              eq(MAX_UNPAUSE_RETRY_COUNT),
              any(TargetSnapshot.class));
    }

    @Test
    void WhenInstantNowThrowExceptionShouldRunUnpause() throws PauserException {
      // Arrange
      MockedStatic<Instant> mockedTime = mockStatic(Instant.class);
      mockedTime.when(() -> Instant.now()).thenThrow(RuntimeException.class);
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "The pause operation failed for some reason. However, the unpause operation succeeded"
              + " afterward. Currently, the scalar products are running with the unpause status."
              + " You should retry the pause operation to ensure proper backup.",
          thrown.getMessage());
      verify(pauser, times(1))
          .unpauseWithRetry(
              any(RequestCoordinator.class),
              eq(MAX_UNPAUSE_RETRY_COUNT),
              any(TargetSnapshot.class));

      mockedTime.close();
    }

    @Test
    void WhenSleepThrowExceptionShouldRunUnpause() throws PauserException {
      // Arrange
      int pauseDuration = 1;
      MockedStatic<Uninterruptibles> mockedSleep = mockStatic(Uninterruptibles.class);
      mockedSleep
          .when(() -> Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.MILLISECONDS))
          .thenThrow(RuntimeException.class);
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "The pause operation failed for some reason. However, the unpause operation succeeded"
              + " afterward. Currently, the scalar products are running with the unpause status."
              + " You should retry the pause operation to ensure proper backup.",
          thrown.getMessage());
      verify(pauser, times(1))
          .unpauseWithRetry(
              any(RequestCoordinator.class),
              eq(MAX_UNPAUSE_RETRY_COUNT),
              any(TargetSnapshot.class));

      mockedSleep.close();
    }

    @Test
    void WhenUnpauseThrowExceptionShouldRunUnpauseAgain() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doThrow(RuntimeException.class)
          .doNothing()
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT, targetSnapshot);

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "The pause operation failed for some reason. However, the unpause operation succeeded"
              + " afterward. Currently, the scalar products are running with the unpause status."
              + " You should retry the pause operation to ensure proper backup.",
          thrown.getMessage());
      verify(pauser, times(2))
          .unpauseWithRetry(
              any(RequestCoordinator.class),
              eq(MAX_UNPAUSE_RETRY_COUNT),
              any(TargetSnapshot.class));
    }

    @Test
    void WhenUnpauseThrowPauserExceptionTwiceShouldRunUnpauseAgain() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doThrow(PauserException.class)
          .doThrow(PauserException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT, targetSnapshot);

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals("unpauseWithRetry() method failed twice.", thrown.getMessage());
      verify(pauser, times(2))
          .unpauseWithRetry(
              any(RequestCoordinator.class),
              eq(MAX_UNPAUSE_RETRY_COUNT),
              any(TargetSnapshot.class));
    }

    @Test
    void WhenUnpauseThrowUnexpectedExceptionTwiceShouldRunUnpauseAgain() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doThrow(RuntimeException.class)
          .doThrow(RuntimeException.class)
          .when(pauser)
          .unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT, targetSnapshot);

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "unpauseWithRetry() method failed twice due to unexpected exception.",
          thrown.getMessage());
      verify(pauser, times(2))
          .unpauseWithRetry(
              any(RequestCoordinator.class),
              eq(MAX_UNPAUSE_RETRY_COUNT),
              any(TargetSnapshot.class));
    }

    @Test
    void WhenSecondTargetSelectorThrowExceptionShouldThrowException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetSnapshot).doThrow(RuntimeException.class).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "Failed to find the target pods to examine if the targets pods were updated during"
              + " paused.",
          thrown.getMessage());
    }

    @Test
    void WhenTargetPodStatusChangedShouldThrowException() throws PauserException {
      // Arrange
      TargetSnapshot targetBeforePause = mock(TargetSnapshot.class);
      TargetSnapshot targetAfterPause = mock(TargetSnapshot.class);
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
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      int pauseDuration = 1;
      Pauser pauser = spy(new Pauser(namespace, helmReleaseName));
      doReturn(targetBeforePause).doReturn(targetAfterPause).when(pauser).getTarget();
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> pauser.pause(pauseDuration, null));
      assertEquals(
          "The target pods were updated during paused. Please retry.", thrown.getMessage());
    }

    @Test
    void WhenPauseSucceededReturnPausedDuration() throws PauserException {
      TargetSnapshot targetBeforePause = mock(TargetSnapshot.class);
      TargetSnapshot targetAfterPause = mock(TargetSnapshot.class);
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
      doReturn(requestCoordinator).when(pauser).getRequestCoordinator(targetSnapshot);
      doNothing().when(requestCoordinator).pause(true, null);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      PausedDuration actual = assertDoesNotThrow(() -> pauser.pause(pauseDuration, 3000L));
      PausedDuration expected = new PausedDuration(startTime, endTime);
      assertEquals(actual.getStartTime(), expected.getStartTime());
      assertEquals(actual.getEndTime(), expected.getEndTime());

      mockedTime.close();
    }
  }

  @Nested
  class MethodUnpauseWithRetry {
    @Test
    void WhenUnpauseSucceededReturnWithoutException() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);
      doNothing().when(requestCoordinator).unpause();

      // Act & Assert
      assertDoesNotThrow(
          () ->
              pauser.unpauseWithRetry(requestCoordinator, MAX_UNPAUSE_RETRY_COUNT, targetSnapshot));
    }

    @Test
    void WhenExceptionOccurShouldRetryThreeTimes() throws PauserException {
      // Arrange
      String namespace = "dummyNs";
      String helmReleaseName = "dummyRelease";
      Pauser pauser = new Pauser(namespace, helmReleaseName);
      doThrow(RuntimeException.class).when(requestCoordinator).unpause();

      // Act & Assert
      PauserException thrown =
          assertThrows(
              PauserException.class,
              () ->
                  pauser.unpauseWithRetry(
                      requestCoordinator, MAX_UNPAUSE_RETRY_COUNT, targetSnapshot));
      assertEquals(
          "Failed to unpause Scalar product. They are still in paused. You must restart related"
              + " pods by using the `kubectl rollout restart deployment "
              + dummyObjectName
              + "` command to"
              + " unpause all pods.",
          thrown.getMessage());
      verify(requestCoordinator, times(MAX_UNPAUSE_RETRY_COUNT)).unpause();
    }
  }
}
