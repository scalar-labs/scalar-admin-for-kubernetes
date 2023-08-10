package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TargetSnapshotTest {

  @Test
  public void getPods_ShouldReturnCorrectPod() {
    // Arrange
    V1Pod pod = mockPod("pod", "podResourceVersion", 1);
    V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");
    TargetSnapshot snapshot = new TargetSnapshot(Arrays.asList(pod), deployment, 8080);

    // Act
    List<V1Pod> pods = snapshot.getPods();

    // Assert
    assertEquals(1, pods.size());
    assertTrue(pods.contains(pod));
  }

  @Test
  public void getDeployment_ShouldReturnCorrectDeployment() {
    // Arrange
    V1Pod pod = mockPod("pod", "podResourceVersion", 1);
    V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");
    TargetSnapshot snapshot = new TargetSnapshot(Arrays.asList(pod), deployment, 8080);

    // Act
    V1Deployment got = snapshot.getDeployment();

    // Assert
    assertEquals(deployment, got);
  }

  @Test
  void getAdminPort_ShouldReturnCorrectAdminPort() {
    // Arrange
    V1Pod pod = mockPod("pod", "podResourceVersion", 1);
    V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");
    TargetSnapshot snapshot = new TargetSnapshot(Arrays.asList(pod), deployment, 8080);

    // Act
    Integer port = snapshot.getAdminPort();

    // Assert
    assertEquals(8080, port);
  }

  @Test
  void getStatus_ShouldReturnCorrectTargetStatus() {
    // Arrange
    V1Pod pod = mockPod("pod", "podResourceVersion", 1);
    V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");
    TargetSnapshot snapshot1 = new TargetSnapshot(Arrays.asList(pod), deployment, 8080);
    TargetSnapshot snapshot2 = new TargetSnapshot(Arrays.asList(pod), deployment, 8080);

    // Act & Assert
    assertTrue(snapshot1.getStatus().equals(snapshot2.getStatus()));
  }

  private V1Pod mockPod(String name, String resourceVersion, Integer restartCount) {
    V1ContainerStatus containerStatus = new V1ContainerStatus();
    containerStatus.setRestartCount(restartCount);

    V1PodStatus podStatus = new V1PodStatus();
    podStatus.setContainerStatuses(Arrays.asList(containerStatus));

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName(name);
    metadata.setResourceVersion(resourceVersion);

    V1Pod pod = new V1Pod();
    pod.setMetadata(metadata);
    pod.setStatus(podStatus);

    return pod;
  }

  private V1Deployment mockDeployment(String name, String resourceVersion) {
    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setResourceVersion(resourceVersion);
    metadata.setName(name);

    V1Deployment deployment = new V1Deployment();
    deployment.setMetadata(metadata);

    return deployment;
  }
}
