package com.scalar.admin.kubernetes.domain.model.pause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PauseTargetTest {

  @Nested
  @DisplayName("Constructor")
  class Constructor {

    @Nested
    @DisplayName("when given valid parameters")
    class WhenGivenValidParameters {

      @Test
      @DisplayName("creates PauseTarget successfully")
      void createsPauseTargetSuccessfully() {
        // Arrange
        V1Pod pod = mockPod("pod", "podResourceVersion", 1);
        V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");

        // Act
        PauseTarget target = new PauseTarget(Arrays.asList(pod), deployment, 8080);

        // Assert
        assertThat(target).isNotNull();
        assertThat(target.pods()).containsExactly(pod);
        assertThat(target.deployment()).isEqualTo(deployment);
        assertThat(target.adminPort()).isEqualTo(8080);
      }
    }

    @Nested
    @DisplayName("when pods is null")
    class WhenPodsIsNull {

      @Test
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException() {
        // Arrange
        V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");

        // Act & Assert
        assertThatThrownBy(() -> new PauseTarget(null, deployment, 8080))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pods must not be null");
      }
    }

    @Nested
    @DisplayName("when deployment is null")
    class WhenDeploymentIsNull {

      @Test
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException() {
        // Arrange
        V1Pod pod = mockPod("pod", "podResourceVersion", 1);

        // Act & Assert
        assertThatThrownBy(() -> new PauseTarget(Arrays.asList(pod), null, 8080))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("deployment must not be null");
      }
    }

    @Nested
    @DisplayName("when adminPort is invalid")
    class WhenAdminPortIsInvalid {

      @ParameterizedTest
      @ValueSource(ints = {0, -1, -100})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(int invalidPort) {
        // Arrange
        V1Pod pod = mockPod("pod", "podResourceVersion", 1);
        V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");

        // Act & Assert
        assertThatThrownBy(() -> new PauseTarget(Arrays.asList(pod), deployment, invalidPort))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("adminPort must be greater than 0");
      }
    }
  }

  @Nested
  @DisplayName("getStatus()")
  class GetStatus {

    @Nested
    @DisplayName("when called on targets with same state")
    class WhenCalledOnTargetsWithSameState {

      @Test
      @DisplayName("returns equal status objects")
      void returnsEqualStatusObjects() {
        // Arrange
        V1Pod pod = mockPod("pod", "podResourceVersion", 1);
        V1Deployment deployment = mockDeployment("deployment", "deploymentResourceVersion");
        PauseTarget target1 = new PauseTarget(Arrays.asList(pod), deployment, 8080);
        PauseTarget target2 = new PauseTarget(Arrays.asList(pod), deployment, 8080);

        // Act & Assert
        assertThat(target1.toStatus()).isEqualTo(target2.toStatus());
      }
    }
  }

  @Nested
  @DisplayName("Status")
  class StatusTest {

    @Nested
    @DisplayName("equals()")
    class Equals {

      @Nested
      @DisplayName("when comparing equal statuses")
      class WhenComparingEqualStatuses {

        @Test
        @DisplayName("returns true")
        void returnsTrue() {
          // Arrange
          Map<String, Integer> podRestartCounts = Map.of("pod1", 1, "pod2", 2);
          Map<String, String> podResourceVersions =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion2");
          String deploymentResourceVersion = "deploymentResourceVersion1";

          PauseTarget.Status status1 =
              new PauseTarget.Status(
                  podRestartCounts, podResourceVersions, deploymentResourceVersion);
          PauseTarget.Status status2 =
              new PauseTarget.Status(
                  podRestartCounts, podResourceVersions, deploymentResourceVersion);

          // Act & Assert
          assertThat(status1).isEqualTo(status2);
        }
      }

      @Nested
      @DisplayName("when pod names differ")
      class WhenPodNamesDiffer {

        @Test
        @DisplayName("returns false")
        void returnsFalse() {
          // Arrange
          Map<String, Integer> podRestartCounts1 = Map.of("pod1", 1, "pod2", 2);
          Map<String, String> podResourceVersions1 =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion2");
          String deploymentResourceVersion = "deploymentResourceVersion1";

          Map<String, Integer> podRestartCounts2 = Map.of("pod1", 1, "different-name", 2);
          Map<String, String> podResourceVersions2 =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion2");

          PauseTarget.Status status1 =
              new PauseTarget.Status(
                  podRestartCounts1, podResourceVersions1, deploymentResourceVersion);
          PauseTarget.Status status2 =
              new PauseTarget.Status(
                  podRestartCounts2, podResourceVersions2, deploymentResourceVersion);

          // Act & Assert
          assertThat(status1).isNotEqualTo(status2);
        }
      }

      @Nested
      @DisplayName("when pod amounts differ")
      class WhenPodAmountsDiffer {

        @Test
        @DisplayName("returns false")
        void returnsFalse() {
          // Arrange
          Map<String, Integer> podRestartCounts1 = Map.of("pod1", 1, "pod2", 2);
          Map<String, String> podResourceVersions1 =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion2");
          String deploymentResourceVersion = "deploymentResourceVersion1";

          Map<String, Integer> podRestartCounts2 = Map.of("pod1", 1);
          Map<String, String> podResourceVersions2 = Map.of("pod1", "resourceVersion1");

          PauseTarget.Status status1 =
              new PauseTarget.Status(
                  podRestartCounts1, podResourceVersions1, deploymentResourceVersion);
          PauseTarget.Status status2 =
              new PauseTarget.Status(
                  podRestartCounts2, podResourceVersions2, deploymentResourceVersion);

          // Act & Assert
          assertThat(status1).isNotEqualTo(status2);
        }
      }

      @Nested
      @DisplayName("when pod restart counts differ")
      class WhenPodRestartCountsDiffer {

        @Test
        @DisplayName("returns false")
        void returnsFalse() {
          // Arrange
          Map<String, Integer> podRestartCounts1 = Map.of("pod1", 1, "pod2", 2);
          Map<String, String> podResourceVersions =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion2");
          String deploymentResourceVersion = "deploymentResourceVersion1";

          Map<String, Integer> podRestartCounts2 = Map.of("pod1", 2, "pod2", 2);

          PauseTarget.Status status1 =
              new PauseTarget.Status(
                  podRestartCounts1, podResourceVersions, deploymentResourceVersion);
          PauseTarget.Status status2 =
              new PauseTarget.Status(
                  podRestartCounts2, podResourceVersions, deploymentResourceVersion);

          // Act & Assert
          assertThat(status1).isNotEqualTo(status2);
        }
      }

      @Nested
      @DisplayName("when pod resource versions differ")
      class WhenPodResourceVersionsDiffer {

        @Test
        @DisplayName("returns false")
        void returnsFalse() {
          // Arrange
          Map<String, Integer> podRestartCounts = Map.of("pod1", 1, "pod2", 2);
          Map<String, String> podResourceVersions1 =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion2");
          String deploymentResourceVersion = "deploymentResourceVersion1";

          Map<String, String> podResourceVersions2 =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion-different");

          PauseTarget.Status status1 =
              new PauseTarget.Status(
                  podRestartCounts, podResourceVersions1, deploymentResourceVersion);
          PauseTarget.Status status2 =
              new PauseTarget.Status(
                  podRestartCounts, podResourceVersions2, deploymentResourceVersion);

          // Act & Assert
          assertThat(status1).isNotEqualTo(status2);
        }
      }

      @Nested
      @DisplayName("when deployment resource versions differ")
      class WhenDeploymentResourceVersionsDiffer {

        @Test
        @DisplayName("returns false")
        void returnsFalse() {
          // Arrange
          Map<String, Integer> podRestartCounts = Map.of("pod1", 1, "pod2", 2);
          Map<String, String> podResourceVersions =
              Map.of("pod1", "resourceVersion1", "pod2", "resourceVersion2");
          String deploymentResourceVersion1 = "deploymentResourceVersion1";
          String deploymentResourceVersion2 = "deploymentResourceVersion-different";

          PauseTarget.Status status1 =
              new PauseTarget.Status(
                  podRestartCounts, podResourceVersions, deploymentResourceVersion1);
          PauseTarget.Status status2 =
              new PauseTarget.Status(
                  podRestartCounts, podResourceVersions, deploymentResourceVersion2);

          // Act & Assert
          assertThat(status1).isNotEqualTo(status2);
        }
      }
    }
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
