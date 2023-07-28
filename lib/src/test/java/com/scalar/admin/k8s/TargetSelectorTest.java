package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TargetSelectorTest {

  private CoreV1Api coreV1Api;
  private AppsV1Api appsV1Api;

  @BeforeEach
  public void setUp() throws ApiException {
    coreV1Api = mock(CoreV1Api.class);
    appsV1Api = mock(AppsV1Api.class);

    mockCoreV1Api();
    mockAppsV1Api();
  }

  @Test
  public void select_coreV1ApiListNamespacedPodThrowApiException_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    when(coreV1Api.listNamespacedPod(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName",
            null,
            null,
            null,
            null,
            null))
        .thenThrow(new ApiException("", 0, null, "mock response body"));

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_coreV1ApiListNamespacedPodReturnEmptyPodList_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1PodList podList = new V1PodList();
    podList.setItems(new ArrayList<V1Pod>());

    when(coreV1Api.listNamespacedPod(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(podList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_podsCreatedByHelmReleaseHaveNoAppLabelValues_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1PodList podList = new V1PodList();

    V1Pod pod = new V1Pod();

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setLabels(new HashMap<>());
    metadata.setName("pod1");
    pod.setMetadata(metadata);

    List<V1Pod> pods = Arrays.asList(pod);
    podList.setItems(pods);

    when(coreV1Api.listNamespacedPod(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(podList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_podsCreatedByHelmReleaseRunTwoScalarProducts_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1PodList podList = new V1PodList();

    V1Pod pod1 = mockPod("pod1", "1", 0, "scalardb");
    V1Pod pod2 = mockPod("pod2", "1", 0, "scalardb-cluster");

    List<V1Pod> pods = Arrays.asList(pod1, pod2);
    podList.setItems(pods);

    when(coreV1Api.listNamespacedPod(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(podList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_podsCreatedByHelmReleaseDontRunAnyScalarProduct_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1PodList podList = new V1PodList();

    V1Pod pod1 = mockPod("pod1", "1", 0, "envoy");
    V1Pod pod2 = mockPod("pod2", "1", 0, "envoy");

    List<V1Pod> pods = Arrays.asList(pod1, pod2);
    podList.setItems(pods);

    when(coreV1Api.listNamespacedPod(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(podList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_appsV1ApiListNamespacedDeploymentReturnEmptyList_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1DeploymentList deploymentList = new V1DeploymentList();
    deploymentList.setItems(new ArrayList<>());

    when(appsV1Api.listNamespacedDeployment(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(deploymentList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void
      select_appsV1ApiListNamespacedDeploymentReturnMoreThanOneDeployment_ShouldThrowException()
          throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1DeploymentList deploymentList = new V1DeploymentList();
    deploymentList.setItems(Arrays.asList(new V1Deployment(), new V1Deployment()));

    when(appsV1Api.listNamespacedDeployment(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(deploymentList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_appsV1ApiListNamespacedDeploymentThrowApiException_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    when(appsV1Api.listNamespacedDeployment(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenThrow(new ApiException("", 0, null, "mock response body"));

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_coreV1ApiListNamespacedServiceThrowApiException_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    when(coreV1Api.listNamespacedService(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenThrow(new ApiException("", 0, null, "mock response body"));

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_coreV1ApiListNamespacedServiceReturnEmptyList_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1ServiceList serviceList = new V1ServiceList();
    serviceList.setItems(new ArrayList<>());

    when(coreV1Api.listNamespacedService(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(serviceList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_serviceCreatedNotRunScalarProduct_ShouldThrowException() throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName("whatever-service-name");

    V1Service service = new V1Service();
    service.setMetadata(metadata);

    V1ServiceList serviceList = new V1ServiceList();
    serviceList.setItems(Arrays.asList(service));

    when(coreV1Api.listNamespacedService(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(serviceList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_servicesCreatedRunMoreThanOneScalarProduct_ShouldThrowException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1ObjectMeta metadata1 = new V1ObjectMeta();
    V1ObjectMeta metadata2 = new V1ObjectMeta();
    metadata1.setName("scalardb-headless");
    metadata2.setName("scalardl-headless");

    V1Service service1 = new V1Service();
    V1Service service2 = new V1Service();
    service1.setMetadata(metadata1);
    service2.setMetadata(metadata2);

    V1ServiceList seriveList = new V1ServiceList();
    seriveList.setItems(Arrays.asList(service1, service2));

    when(coreV1Api.listNamespacedService(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(seriveList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_serviceDoesntHavePort_ShouldThrowException() throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName("scalardb-headless");

    V1ServiceSpec serviceSpec = new V1ServiceSpec();
    serviceSpec.setPorts(new ArrayList<>());

    V1Service service = new V1Service();
    service.setMetadata(metadata);
    service.setSpec(serviceSpec);

    V1ServiceList serviceList = new V1ServiceList();
    serviceList.setItems(Arrays.asList(service));

    when(coreV1Api.listNamespacedService(
            "namespace",
            null,
            null,
            null,
            null,
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(serviceList);

    // Act & Assert
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);

    Throwable thrown = assertThrows(Exception.class, () -> targetSelector.select());

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_NormalCase_ShouldReturnTargetSnapshot() throws Exception {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    // Act
    TargetSelector targetSelector =
        new TargetSelector(coreV1Api, appsV1Api, namespace, helmReleaseName);
    TargetSnapshot target = targetSelector.select();

    // Assert
    assertEquals(1, target.getAdminPort());

    List<V1Pod> pods = target.getPods();
    assertEquals(2, pods.size());

    List<String> podNames =
        pods.stream().map(p -> p.getMetadata().getName()).collect(Collectors.toList());
    assertTrue(podNames.contains("pod1"));
    assertTrue(podNames.contains("pod2"));
  }

  private void mockCoreV1Api() throws ApiException {
    List<V1Pod> pods =
        Arrays.asList(mockPod("pod1", "1", 0, "scalardb"), mockPod("pod2", "2", 0, "scalardb"));
    V1PodList podList = new V1PodList();
    podList.setItems(pods);

    when(coreV1Api.listNamespacedPod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(podList);

    List<V1Service> services = Arrays.asList(mockService("scalardb", "scalardb", 1));
    V1ServiceList serviceList = new V1ServiceList();
    serviceList.setItems(services);

    when(coreV1Api.listNamespacedService(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(serviceList);
  }

  private void mockAppsV1Api() throws ApiException {
    V1DeploymentList deploymentList = new V1DeploymentList();
    List<V1Deployment> deployments = Arrays.asList(mockDeployment("deployment1", "1", "scalardb"));
    deploymentList.setItems(deployments);

    when(appsV1Api.listNamespacedDeployment(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(deploymentList);
  }

  private V1Pod mockPod(
      String name, String resourceVersion, Integer restartCount, String appLabelValue) {
    V1ContainerStatus containerStatus = new V1ContainerStatus();
    containerStatus.setRestartCount(restartCount);

    V1PodStatus podStatus = new V1PodStatus();
    podStatus.setContainerStatuses(Arrays.asList(containerStatus));

    Map<String, String> labels = new HashMap<>();
    labels.put("app.kubernetes.io/app", appLabelValue);

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setLabels(labels);
    metadata.setName(name);
    metadata.setResourceVersion(resourceVersion);

    V1Pod pod = new V1Pod();
    pod.setMetadata(metadata);
    pod.setStatus(podStatus);

    return pod;
  }

  private V1Deployment mockDeployment(String name, String resourceVersion, String appLabelValue) {
    Map<String, String> labels = new HashMap<>();
    labels.put("app.kubernetes.io/app", appLabelValue);

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setLabels(labels);
    metadata.setResourceVersion(resourceVersion);
    metadata.setName(name);

    V1Deployment deployment = new V1Deployment();
    deployment.setMetadata(metadata);

    return deployment;
  }

  private V1Service mockService(String appLabelValue, String portName, Integer targetPort) {
    Map<String, String> labels = new HashMap<>();
    labels.put("app.kubernetes.io/app", appLabelValue);

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setLabels(labels);
    metadata.setName(appLabelValue + "-headless");

    V1ServicePort servicePort = new V1ServicePort();
    servicePort.setName(portName);
    servicePort.setTargetPort(new IntOrString(targetPort));

    V1ServiceSpec serviceSpec = new V1ServiceSpec();
    serviceSpec.setPorts(Arrays.asList(servicePort));

    V1Service service = new V1Service();
    service.setMetadata(metadata);
    service.setSpec(serviceSpec);

    return service;
  }
}
