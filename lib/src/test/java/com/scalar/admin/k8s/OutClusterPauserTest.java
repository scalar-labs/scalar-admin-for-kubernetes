package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Uninterruptibles;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class OutClusterPauserTest {
  private KubernetesClient k8sClientMocked;
  private CoreV1Api coreV1ApiMocked;
  private MockedStatic<Uninterruptibles> uninterruptiblesMocked;

  @BeforeEach
  public void setUp() {
    k8sClientMocked = mock(KubernetesClient.class);
    coreV1ApiMocked = mock(CoreV1Api.class);

    uninterruptiblesMocked = mockStatic(Uninterruptibles.class);
  }

  @AfterEach
  public void tearDown() {
    uninterruptiblesMocked.close();
  }

  @Test
  public void pause_WithNormalCondition_ShouldCallDependenciesProperly() throws ApiException {
    // Arrange
    String namespace = "namespace-1";
    V1Pod pod = mockPod();
    Integer adminPort = 100;
    Integer duration = 10;

    mockK8sClient();

    OutClusterPauser outClusterPauser = new OutClusterPauser(k8sClientMocked, namespace);

    // Act
    outClusterPauser.pause(Arrays.asList(pod), adminPort, duration);

    // Asset
    ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> podNameCaptor = ArgumentCaptor.forClass(String.class);

    verify(coreV1ApiMocked, times(2))
        .readNamespacedPod(podNameCaptor.capture(), namespaceCaptor.capture(), any());
    verify(coreV1ApiMocked, times(2))
        .deleteNamespacedPod(
            podNameCaptor.capture(),
            namespaceCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
    ;
    verify(coreV1ApiMocked, times(2))
        .createNamespacedPod(namespaceCaptor.capture(), any(), any(), any(), any(), any());

    assertTrue(namespaceCaptor.getAllValues().stream().allMatch(v -> v.equals("namespace-1")));
    assertTrue(
        podNameCaptor.getAllValues().stream().anyMatch(v -> v.contains("scalar-admin-pause")));
    assertTrue(
        podNameCaptor.getAllValues().stream().anyMatch(v -> v.contains("scalar-admin-unpause")));
  }

  private void mockK8sClient() throws ApiException {
    when(coreV1ApiMocked.readNamespacedPod(anyString(), anyString(), any())).thenReturn(mockPod());
    when(k8sClientMocked.getCoreV1Api()).thenReturn(coreV1ApiMocked);
  }

  private V1Pod mockPod() {
    V1Pod pod = new V1Pod();
    V1PodStatus status = new V1PodStatus();
    V1ObjectMeta metadata = new V1ObjectMeta();

    metadata.setName("pod-1");
    status.setPodIP("ip");
    status.setPhase("Succeeded");
    pod.setStatus(status);
    pod.setMetadata(metadata);

    return pod;
  }
}
