package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TargetPodsTest {

  private KubernetesClient k8sClientMocked;
  private CoreV1Api coreV1ApiMocked;
  private AppsV1Api appsV1ApiMocked;
  private V1PodList podListMocked;

  @BeforeEach
  public void setUp() {
    k8sClientMocked = mock(KubernetesClient.class);
    coreV1ApiMocked = mock(CoreV1Api.class);
    appsV1ApiMocked = mock(AppsV1Api.class);
    podListMocked = mock(V1PodList.class);
  }

  @Test
  public void findTargetPods_WithNormalCondition_ShouldCallDependenciesProperly()
      throws ApiException {
    // Arrange
    String namespace = "namespace-1";
    String helmReleaseName = "helm-release-1";
    String productType = "scalardb";
    Integer adminPort = 100;

    mockK8sClient();

    // Act
    TargetPods targetPods =
        TargetPods.findTargetPods(
            k8sClientMocked, namespace, helmReleaseName, productType, adminPort);

    // Asset
    assertEquals(100, targetPods.getAdminPort());
    assertEquals("scalardb", targetPods.getProduct().getType());
    assertEquals(1, targetPods.getPods().size());

    verify(coreV1ApiMocked, atLeastOnce())
        .listNamespacedPod(
            namespace,
            null,
            null,
            null,
            null,
            TargetPods.LABEL_INSTANCE + "=" + helmReleaseName,
            null,
            null,
            null,
            null,
            null);
    verify(appsV1ApiMocked, atLeastOnce())
        .readNamespacedDeployment("helm-release-1-scalardb", namespace, null);
  }

  @Test
  public void findTargetPods_WithDifferentProductPods_ShouldThrowException() throws ApiException {
    // Arrange
    String namespace = "namespace-2";
    String helmReleaseName = "helm-release-2";
    String productType = "scalardb";
    Integer adminPort = 100;

    mockK8sClient();

    List<V1Pod> pods =
        Arrays.asList(
            mockPod("pod1", "1", 0, "scalardb"), mockPod("pod2", "1", 0, "scalardb-cluster"));
    when(podListMocked.getItems()).thenReturn(pods);

    // Act & Assert
    Throwable thrown =
        assertThrows(
            RuntimeException.class,
            () -> {
              TargetPods.findTargetPods(
                  k8sClientMocked, namespace, helmReleaseName, productType, adminPort);
            });
    assertEquals(
        "Multiple Scalar products are found in the same release. This should not happen. Please"
            + " make sure you deploy Scalar products with Scalar Helm Charts",
        thrown.getMessage());
  }

  @Test
  public void findTargetPods_WithDifferentProductSpecified_ShouldReturnZeroSize()
      throws ApiException {
    // Arrange
    String namespace = "namespace-2";
    String helmReleaseName = "helm-release-2";
    String productType = "scalardb-cluster";
    Integer adminPort = 100;

    mockK8sClient();

    List<V1Pod> pods = Arrays.asList(mockPod("pod1", "1", 0, "scalardb"));
    when(podListMocked.getItems()).thenReturn(pods);

    // Act
    TargetPods targetPods =
        TargetPods.findTargetPods(
            k8sClientMocked, namespace, helmReleaseName, productType, adminPort);

    // Assert
    assertEquals(0, targetPods.getPods().size());
  }

  @Test
  public void findTargetPods_WithDifferentProductSpecified_ShouldOnlyReturnSpecifiedPods()
      throws ApiException {
    // Arrange
    String namespace = "namespace-2";
    String helmReleaseName = "helm-release-2";
    String productType = "scalardb-cluster";
    Integer adminPort = 100;

    mockK8sClient();

    List<V1Pod> pods =
        Arrays.asList(
            mockPod("pod1", "1", 0, "scalardb"), mockPod("pod2", "1", 0, "scalardb-cluster"));
    when(podListMocked.getItems()).thenReturn(pods);

    // Act
    TargetPods targetPods =
        TargetPods.findTargetPods(
            k8sClientMocked, namespace, helmReleaseName, productType, adminPort);

    // Assert
    assertEquals(1, targetPods.getPods().size());
    assertEquals("pod2", targetPods.getPods().get(0).getMetadata().getName());
  }

  @Test
  public void isUpdated_WithPodCountUpdated_ShouldBeTrue() throws ApiException {
    // Arrange
    String namespace = "namespace-3";
    String helmReleaseName = "helm-release-3";
    String productType = "scalardb";
    Integer adminPort = 100;

    mockK8sClient();

    List<V1Pod> pods1 = Arrays.asList(mockPod("pod1", "1", 0, "scalardb"));
    List<V1Pod> pods2 =
        Arrays.asList(mockPod("pod1", "1", 0, "scalardb"), mockPod("pod2", "1", 0, "scalardb"));

    when(podListMocked.getItems()).thenReturn(pods1).thenReturn(pods2);

    // Act
    TargetPods targetPods =
        TargetPods.findTargetPods(
            k8sClientMocked, namespace, helmReleaseName, productType, adminPort);

    // Asset
    assertTrue(targetPods.isUpdated());
  }

  @Test
  public void isUpdated_WithPodResourceVersionUpdated_ShouldBeTrue() throws ApiException {
    // Arrange
    String namespace = "namespace-4";
    String helmReleaseName = "helm-release-4";
    String productType = "scalardb";
    Integer adminPort = 100;

    mockK8sClient();

    List<V1Pod> pods1 = Arrays.asList(mockPod("pod1", "1", 0, "scalardb"));
    List<V1Pod> pods2 = Arrays.asList(mockPod("pod1", "2", 0, "scalardb"));

    when(podListMocked.getItems()).thenReturn(pods1).thenReturn(pods2);

    // Act
    TargetPods targetPods =
        TargetPods.findTargetPods(
            k8sClientMocked, namespace, helmReleaseName, productType, adminPort);

    // Asset
    assertTrue(targetPods.isUpdated());
  }

  @Test
  public void isUpdated_WithPodRestartUpdated_ShouldBeTrue() throws ApiException {
    // Arrange
    String namespace = "namespace-4";
    String helmReleaseName = "helm-release-4";
    String productType = "scalardb";
    Integer adminPort = 100;

    mockK8sClient();

    List<V1Pod> pods1 = Arrays.asList(mockPod("pod1", "1", 0, "scalardb"));
    List<V1Pod> pods2 = Arrays.asList(mockPod("pod1", "1", 1, "scalardb"));

    when(podListMocked.getItems()).thenReturn(pods1).thenReturn(pods2);

    // Act
    TargetPods targetPods =
        TargetPods.findTargetPods(
            k8sClientMocked, namespace, helmReleaseName, productType, adminPort);

    // Asset
    assertTrue(targetPods.isUpdated());
  }

  @Test
  public void isUpdated_WithDeploymentResourceVersionUpdated_ShouldBeTrue() throws ApiException {
    // Arrange
    String namespace = "namespace-4";
    String helmReleaseName = "helm-release-4";
    String productType = "scalardb";
    Integer adminPort = 100;

    mockK8sClient();

    V1Deployment deployment1 = mockDeployment("1");
    V1Deployment deployment2 = mockDeployment("2");
    when(appsV1ApiMocked.readNamespacedDeployment(any(), any(), any()))
        .thenReturn(deployment1)
        .thenReturn(deployment2);

    // Act
    TargetPods targetPods =
        TargetPods.findTargetPods(
            k8sClientMocked, namespace, helmReleaseName, productType, adminPort);

    // Asset
    assertTrue(targetPods.isUpdated());
  }

  private void mockK8sClient() throws ApiException {
    List<V1Pod> pods = Arrays.asList(mockPod("pod1", "1", 0, "scalardb"));
    when(podListMocked.getItems()).thenReturn(pods);
    when(coreV1ApiMocked.listNamespacedPod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(podListMocked);

    V1Deployment deployment = mockDeployment("1");
    when(appsV1ApiMocked.readNamespacedDeployment(any(), any(), any())).thenReturn(deployment);

    when(k8sClientMocked.getCoreV1Api()).thenReturn(coreV1ApiMocked);
    when(k8sClientMocked.getAppsV1Api()).thenReturn(appsV1ApiMocked);
  }

  private V1Pod mockPod(
      String name, String resourceVersion, Integer restartCount, String appLabel) {
    V1ContainerStatus containerStatus = mock(V1ContainerStatus.class);
    when(containerStatus.getRestartCount()).thenReturn(restartCount);

    V1PodStatus podStatus = mock(V1PodStatus.class);
    when(podStatus.getContainerStatuses()).thenReturn(Arrays.asList(containerStatus));

    V1ObjectMeta metadata = mock(V1ObjectMeta.class);
    when(metadata.getName()).thenReturn(name);
    when(metadata.getResourceVersion()).thenReturn(resourceVersion);
    when(metadata.getLabels())
        .thenReturn(
            new HashMap<String, String>() {
              {
                put(TargetPods.LABEL_APP, appLabel);
              }
            });

    V1Pod pod = mock(V1Pod.class);
    when(pod.getMetadata()).thenReturn(metadata);
    when(pod.getStatus()).thenReturn(podStatus);

    return pod;
  }

  private V1Deployment mockDeployment(String resourceVersion) {
    V1ObjectMeta metadata = mock(V1ObjectMeta.class);
    when(metadata.getResourceVersion()).thenReturn(resourceVersion);

    V1Deployment deployment = mock(V1Deployment.class);
    when(deployment.getMetadata()).thenReturn(metadata);

    return deployment;
  }
}
