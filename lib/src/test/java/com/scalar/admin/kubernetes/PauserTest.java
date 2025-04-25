package com.scalar.admin.kubernetes;

import static com.scalar.admin.kubernetes.Pauser.MAX_UNPAUSE_RETRY_COUNT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Config;
import java.io.IOException;
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
      doNothing().when(requestCoordinator).unpause();

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
      RuntimeException thrown =
          assertThrows(RuntimeException.class, () -> pauser.pause(pauseDuration, null));
      verify(pauser, times(1))
          .unpauseWithRetry(
              any(RequestCoordinator.class),
              eq(MAX_UNPAUSE_RETRY_COUNT),
              any(TargetSnapshot.class));
    }

    @Test
    void WhenInstantNowThrowExceptionShouldRunUnpause() {}

    @Test
    void WhenSleepThrowExceptionShouldRunUnpause() {}

    @Test
    void WhenUnpauseThrowExceptionShouldRunUnpauseAgain() {}

    @Test
    void WhenSecondTargetSelectorThrowExceptionShouldThrowException() {}

    @Test
    void WhenTargetPodStatusChangedShouldThrowException() {}

    @Test
    void WhenPauseSucceededReturnPausedDuration() {}
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
