package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class InClusterPauserTest {
  private MockedConstruction<RequestCoordinator> requestCoordinatorMocked;
  private MockedStatic<Uninterruptibles> uninterruptiblesMocked;

  @BeforeEach
  public void setUp() {
    requestCoordinatorMocked = mockConstruction(RequestCoordinator.class);
    uninterruptiblesMocked = mockStatic(Uninterruptibles.class);
  }

  @AfterEach
  void tearDown() {
    requestCoordinatorMocked.close();
    uninterruptiblesMocked.close();
  }

  @Test
  public void pause_WithNormalCondition_ShouldCallDependenceProperly() {
    // Arrange
    Integer duration = 10;
    Integer adminPort = 100;
    V1Pod pod = mockPod();

    // Act
    new InClusterPauser().pause(Arrays.asList(pod), adminPort, duration);

    // Assest
    assertEquals(1, requestCoordinatorMocked.constructed().size());
    verify(requestCoordinatorMocked.constructed().get(0), times(1)).pause(true, Long.valueOf(0));
    verify(requestCoordinatorMocked.constructed().get(0), times(1)).unpause();
    uninterruptiblesMocked.verify(
        () -> Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS), times(1));
  }

  private V1Pod mockPod() {
    V1Pod pod = new V1Pod();
    V1PodStatus status = new V1PodStatus();
    status.setPodIP("ip");
    pod.setStatus(status);

    return pod;
  }
}
