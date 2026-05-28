package com.scalar.admin.kubernetes.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1LabelSelector;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class KubernetesClientImplTest {

  private CoreV1Api coreV1Api;
  private AppsV1Api appsV1Api;

  @BeforeEach
  public void setUp() throws ApiException {
    coreV1Api = mock(CoreV1Api.class);
    appsV1Api = mock(AppsV1Api.class);

    mockCoreV1Api();
    mockAppsV1ApiForListNamespacedDeployment();
  }

  
  @Test
  public void select_coreV1ApiListNamespacedPodThrowApiException_ShouldThrowPauserException()
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
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_coreV1ApiListNamespacedPodReturnEmptyPodList_ShouldThrowPauserException()
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
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_podsCreatedByHelmReleaseHaveNoAppLabelValues_ShouldThrowPauserException()
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
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_podsCreatedByHelmReleaseRunTwoScalarProducts_ShouldThrowPauserException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1PodList podList = new V1PodList();

    V1Pod pod1 = mockPod("pod1", "1", 0, "scalardb-cluster");
    V1Pod pod2 = mockPod("pod2", "1", 0, "ledger");

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
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_podsCreatedByHelmReleaseDontRunAnyScalarProduct_ShouldThrowPauserException()
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
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_appsV1ApiListNamespacedDeploymentReturnEmptyList_ShouldThrowPauserException()
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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(deploymentList);

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void
      select_appsV1ApiListNamespacedDeploymentReturnMoreThanOneDeployment_ShouldThrowPauserException()
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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(deploymentList);

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_appsV1ApiListNamespacedDeploymentThrowApiException_ShouldThrowPauserException()
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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenThrow(new ApiException("", 0, null, "mock response body"));

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_coreV1ApiListNamespacedServiceThrowApiException_ShouldThrowPauserException()
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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenThrow(new ApiException("", 0, null, "mock response body"));

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_coreV1ApiListNamespacedServiceReturnEmptyList_ShouldThrowPauserException()
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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(serviceList);

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_serviceCreatedNotRunScalarProduct_ShouldThrowPauserException()
      throws ApiException {
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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(serviceList);

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_servicesCreatedRunMoreThanOneScalarProduct_ShouldThrowPauserException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1ObjectMeta metadata1 = new V1ObjectMeta();
    V1ObjectMeta metadata2 = new V1ObjectMeta();
    metadata1.setName("scalardb-cluster-headless");
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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(seriveList);

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_serviceDoesntHavePort_ShouldThrowPauserException() throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName("scalardb-cluster-headless");

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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(serviceList);

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_serviceUsesPortDefinitionInTargetPort_ShouldThrowPauserException()
      throws ApiException {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName("scalardb-cluster-headless");

    V1ServicePort servicePort = new V1ServicePort();
    servicePort.setName("scalardb-cluster");
    servicePort.setTargetPort(new IntOrString("some-port"));

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
            "app.kubernetes.io/instance=helmReleaseName,app.kubernetes.io/app=scalardb-cluster",
            null,
            null,
            null,
            null,
            null))
        .thenReturn(serviceList);

    // Act & Assert
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);

    Throwable thrown = assertThrows(PauserException.class, () -> kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName));

    assertEquals("Can not find any target pods.", thrown.getMessage());
  }

  @Test
  public void select_NormalCase_ShouldReturnPauseTarget() throws Exception {
    // Arrange
    String namespace = "namespace";
    String helmReleaseName = "helmReleaseName";

    // Act
    KubernetesClientImpl kubernetesClient =
        new KubernetesClientImpl(coreV1Api, appsV1Api);
    PauseTarget target = kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName);

    // Assert
    assertEquals(1, target.adminPort());

    List<V1Pod> pods = target.pods();
    assertEquals(2, pods.size());

    List<String> podNames =
        pods.stream().map(p -> p.getMetadata().getName()).collect(Collectors.toList());
    assertTrue(podNames.contains("pod1"));
    assertTrue(podNames.contains("pod2"));
  }

  @Nested
  @DisplayName("getDeployment()")
  class GetDeployment {

    @BeforeEach
    void setUp() throws ApiException {
      mockAppsV1ApiForReadNamespacedDeployment();
    }

    @Nested
    @DisplayName("when deployment exists")
    class WhenDeploymentExists {

      @Test
      @DisplayName("returns V1Deployment")
      void returnsV1Deployment() throws ApiException, PauserException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act
        V1Deployment deployment = kubernetesClient.getDeployment(namespace, deploymentName);

        // Assert
        assertEquals(deploymentName, deployment.getMetadata().getName());
        assertEquals(namespace, deployment.getMetadata().getNamespace());
      }
    }

    @Nested
    @DisplayName("when readNamespacedDeployment throws ApiException")
    class WhenReadNamespacedDeploymentThrowsApiException {

      @Test
      @DisplayName("throws PauserException")
      void throwsPauserException() throws ApiException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";

        when(appsV1Api.readNamespacedDeployment(deploymentName, namespace, null))
            .thenThrow(new ApiException("", 404, null, "mock response body"));

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act & Assert
        assertThatThrownBy(() -> kubernetesClient.getDeployment(namespace, deploymentName))
            .isInstanceOf(PauserException.class)
            .hasMessageContaining("Kubernetes readNamespacedDeployment API error with code 404");
      }
    }
  }

  @Nested
  @DisplayName("extractLabelSelectorFromDeployment()")
  class BuildLabelSelectorFromDeployment {

    @Nested
    @DisplayName("when deployment has valid selector")
    class WhenDeploymentHasValidSelector {

      @Test
      @DisplayName("returns label selector string")
      void returnsLabelSelectorString() throws ApiException, PauserException {
        // Arrange
        String deploymentName = "scalardb-cluster";

        V1Deployment deployment =
            mockDeploymentWithSelector(deploymentName, "v1", "scalardb-cluster");
        deployment.getMetadata().setNamespace("default");
        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act
        String labelSelector = kubernetesClient.extractLabelSelectorFromDeployment(deployment);

        // Assert
        // The label selector format is "key = value" (with spaces)
        assertEquals("app.kubernetes.io/app = scalardb-cluster", labelSelector);
      }
    }

    @Nested
    @DisplayName("when deployment has no spec")
    class WhenDeploymentHasNoSpec {

      @Test
      @DisplayName("throws PauserException")
      void throwsPauserException() throws ApiException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";

        V1Deployment deployment = new V1Deployment();
        deployment.setMetadata(new V1ObjectMeta().name(deploymentName).namespace(namespace));
        deployment.setSpec(null);

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act & Assert
        assertThatThrownBy(() -> kubernetesClient.extractLabelSelectorFromDeployment(deployment))
            .isInstanceOf(PauserException.class)
            .hasMessageContaining("does not have a spec");
      }
    }

    @Nested
    @DisplayName("when deployment has no selector")
    class WhenDeploymentHasNoSelector {

      @Test
      @DisplayName("throws PauserException")
      void throwsPauserException() throws ApiException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";

        V1Deployment deployment = new V1Deployment();
        deployment.setMetadata(new V1ObjectMeta().name(deploymentName).namespace(namespace));
        deployment.setSpec(new V1DeploymentSpec().selector(null));

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act & Assert
        assertThatThrownBy(() -> kubernetesClient.extractLabelSelectorFromDeployment(deployment))
            .isInstanceOf(PauserException.class)
            .hasMessageContaining("does not have a selector");
      }
    }

    @Nested
    @DisplayName("when deployment has empty selector")
    class WhenDeploymentHasEmptySelector {

      @Test
      @DisplayName("throws PauserException")
      void throwsPauserException() throws ApiException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";

        V1Deployment deployment = new V1Deployment();
        deployment.setMetadata(new V1ObjectMeta().name(deploymentName).namespace(namespace));
        deployment.setSpec(
            new V1DeploymentSpec()
                .selector(new V1LabelSelector().matchLabels(null).matchExpressions(null)));

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act & Assert
        assertThatThrownBy(() -> kubernetesClient.extractLabelSelectorFromDeployment(deployment))
            .isInstanceOf(PauserException.class)
            .hasMessageContaining("has an empty selector");
      }
    }

    @Nested
    @DisplayName("when deployment has invalid selector operator")
    class WhenDeploymentHasInvalidSelectorOperator {

      @Test
      @DisplayName("throws PauserException")
      void throwsPauserException() throws ApiException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";

        V1Deployment deployment = new V1Deployment();
        deployment.setMetadata(new V1ObjectMeta().name(deploymentName).namespace(namespace));
        deployment.setSpec(
            new V1DeploymentSpec()
                .selector(
                    new V1LabelSelector()
                        .addMatchExpressionsItem(
                            new io.kubernetes.client.openapi.models.V1LabelSelectorRequirement()
                                .key("app")
                                .operator("InvalidOperator")
                                .addValuesItem("scalardb-cluster"))));

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act & Assert
        assertThatThrownBy(() -> kubernetesClient.extractLabelSelectorFromDeployment(deployment))
            .isInstanceOf(PauserException.class)
            .hasMessageContaining("has an invalid selector");
      }
    }
  }

  @Nested
  @DisplayName("findPodsByLabelSelector()")
  class FindPodsByLabelSelector {

    @BeforeEach
    void setUp() throws ApiException {
      mockCoreV1Api();
    }

    @Nested
    @DisplayName("when pods are found")
    class WhenPodsAreFound {

      @Test
      @DisplayName("returns list of V1Pod")
      void returnsListOfV1Pod() throws ApiException, PauserException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";
        String labelSelector = "app=scalardb-cluster";

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act
        List<V1Pod> pods =
            kubernetesClient.findPodsByLabelSelector(namespace, deploymentName, labelSelector);

        // Assert
        assertEquals(2, pods.size());
      }
    }

    @Nested
    @DisplayName("when listNamespacedPod throws ApiException")
    class WhenListNamespacedPodThrowsApiException {

      @Test
      @DisplayName("throws PauserException")
      void throwsPauserException() throws ApiException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";
        String labelSelector = "app=scalardb-cluster";

        when(coreV1Api.listNamespacedPod(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new ApiException("", 0, null, "mock response body"));

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act & Assert
        assertThatThrownBy(
                () ->
                    kubernetesClient.findPodsByLabelSelector(namespace, deploymentName, labelSelector))
            .isInstanceOf(PauserException.class)
            .hasMessageContaining("Kubernetes listNamespacedPod API error with code 0");
      }
    }

    @Nested
    @DisplayName("when no pods are found")
    class WhenNoPodsAreFound {

      @Test
      @DisplayName("throws PauserException")
      void throwsPauserException() throws ApiException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";
        String labelSelector = "app=scalardb-cluster";

        when(coreV1Api.listNamespacedPod(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new V1PodList().items(new ArrayList<>()));

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act & Assert
        assertThatThrownBy(
                () ->
                    kubernetesClient.findPodsByLabelSelector(namespace, deploymentName, labelSelector))
            .isInstanceOf(PauserException.class)
            .hasMessageContaining("does not have any running pods");
      }
    }
  }

  @Nested
  @DisplayName("resolvePauseTargetByDeploymentName()")
  class FindByDeploymentName {

    @BeforeEach
    void setUp() throws ApiException {
      mockCoreV1Api();
      mockAppsV1ApiForReadNamespacedDeployment();
    }

    @Nested
    @DisplayName("when all components work correctly")
    class WhenAllComponentsWorkCorrectly {

      @Test
      @DisplayName("returns PauseTarget with correct pods, deployment, and adminPort")
      void returnsPauseTargetWithCorrectPodsDeploymentAndAdminPort()
          throws ApiException, PauserException {
        // Arrange
        String namespace = "default";
        String deploymentName = "scalardb-cluster";
        int adminPort = 60054;

        KubernetesClientImpl kubernetesClient =
            new KubernetesClientImpl(coreV1Api, appsV1Api);

        // Act
        PauseTarget pauseTarget =
            kubernetesClient.resolvePauseTargetByDeploymentName(namespace, deploymentName, adminPort);

        // Assert
        assertEquals(2, pauseTarget.pods().size());
        assertEquals(deploymentName, pauseTarget.deployment().getMetadata().getName());
        assertEquals(adminPort, pauseTarget.adminPort());
      }
    }
  }

  private void mockCoreV1Api() throws ApiException {
    List<V1Pod> pods =
        Arrays.asList(mockPod("pod1", "1", 0, "scalardb-cluster"), mockPod("pod2", "2", 0, "scalardb-cluster"));
    V1PodList podList = new V1PodList();
    podList.setItems(pods);

    when(coreV1Api.listNamespacedPod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(podList);

    List<V1Service> services = Arrays.asList(mockService("scalardb-cluster", "scalardb-cluster", 1));
    V1ServiceList serviceList = new V1ServiceList();
    serviceList.setItems(services);

    when(coreV1Api.listNamespacedService(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(serviceList);
  }

  private void mockAppsV1ApiForListNamespacedDeployment() throws ApiException {
    // Mock listNamespacedDeployment - returns deployments without selector
    V1DeploymentList deploymentList = new V1DeploymentList();
    List<V1Deployment> deployments = Arrays.asList(mockDeployment("deployment1", "1", "scalardb-cluster"));
    deploymentList.setItems(deployments);

    when(appsV1Api.listNamespacedDeployment(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(deploymentList);
  }

  private void mockAppsV1ApiForReadNamespacedDeployment() throws ApiException {
    // Mock readNamespacedDeployment - returns deployment with selector
    V1Deployment deployment = mockDeploymentWithSelector("scalardb-cluster", "1", "scalardb");
    deployment.getMetadata().setNamespace("default");
    when(appsV1Api.readNamespacedDeployment(any(), any(), any())).thenReturn(deployment);
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

  private V1Deployment mockDeploymentWithSelector(
      String name, String resourceVersion, String appLabelValue) {
    Map<String, String> labels = new HashMap<>();
    labels.put("app.kubernetes.io/app", appLabelValue);

    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setLabels(labels);
    metadata.setResourceVersion(resourceVersion);
    metadata.setName(name);

    // Create selector with matchLabels
    Map<String, String> matchLabels = new HashMap<>();
    matchLabels.put("app.kubernetes.io/app", appLabelValue);

    V1LabelSelector selector = new V1LabelSelector();
    selector.setMatchLabels(matchLabels);

    V1DeploymentSpec spec = new V1DeploymentSpec();
    spec.setSelector(selector);

    V1Deployment deployment = new V1Deployment();
    deployment.setMetadata(metadata);
    deployment.setSpec(spec);

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
