package com.scalar.admin.kubernetes.domain.model.pause;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a pause target in the Kubernetes cluster.
 *
 * <p>This aggregate root encapsulates all information about the target of a pause operation,
 * including the pods, deployment, and admin port. It provides methods to extract status information
 * for comparison purposes.
 *
 * <p>Note: This record deliberately uses Kubernetes SDK types (V1Pod, V1Deployment) rather than
 * custom domain models. Since this project is a Kubernetes-specific tool, the SDK types are treated
 * as part of the domain vocabulary. A future refactoring may introduce domain-specific models (e.g.
 * PausePod) to decouple from the SDK and enable validation at construction time.
 *
 * @param pods the list of pods that are part of this pause target
 * @param deployment the deployment associated with this pause target
 * @param adminPort the admin port number used for pause operations
 */
public record PauseTarget(List<V1Pod> pods, V1Deployment deployment, int adminPort) {

  /**
   * Compact constructor with immutability enforcement.
   *
   * @param pods the list of pods that are part of this pause target
   * @param deployment the deployment associated with this pause target
   * @param adminPort the admin port number used for pause operations
   */
  public PauseTarget {
    if (pods == null) {
      throw new IllegalArgumentException("pods must not be null");
    }
    if (deployment == null) {
      throw new IllegalArgumentException("deployment must not be null");
    }
    if (adminPort < 1) {
      throw new IllegalArgumentException("adminPort must be greater than 0");
    }
    pods = ImmutableList.copyOf(pods);
  }

  /**
   * Converts this pause target to its status representation.
   *
   * <p>The status includes pod restart counts, pod resource versions, and deployment resource
   * version. This information can be used to detect if pods or deployments have been updated during
   * a pause operation.
   *
   * @return a Status object containing the current status information
   */
  public Status toStatus() {
    Map<String, Integer> podRestartCounts = new HashMap<>();
    Map<String, String> podResourceVersions = new HashMap<>();

    for (V1Pod pod : pods) {
      String podName = pod.getMetadata().getName();
      Integer restartCount =
          pod.getStatus().getContainerStatuses().stream().mapToInt(c -> c.getRestartCount()).sum();
      String resourceVersion = pod.getMetadata().getResourceVersion();

      podRestartCounts.put(podName, restartCount);
      podResourceVersions.put(podName, resourceVersion);
    }

    String deploymentResourceVersion = deployment.getMetadata().getResourceVersion();

    return new Status(podRestartCounts, podResourceVersions, deploymentResourceVersion);
  }

  /**
   * Builds a list of socket addresses from the pods' IPs and the admin port.
   *
   * <p>Pods without a reachable IP (null status or null pod IP) are filtered out. If no pods have a
   * reachable IP, an {@link IllegalStateException} is thrown.
   *
   * @return list of socket addresses for communicating with the target pods
   * @throws IllegalStateException if no target pods have a reachable IP
   */
  public List<InetSocketAddress> toAddressList() {
    List<InetSocketAddress> addresses =
        pods.stream()
            .filter(pod -> pod.getStatus() != null && pod.getStatus().getPodIP() != null)
            .map(pod -> new InetSocketAddress(pod.getStatus().getPodIP(), adminPort))
            .collect(Collectors.toList());
    if (addresses.isEmpty()) {
      throw new IllegalStateException("No target pods have a reachable IP.");
    }
    return addresses;
  }

  /**
   * Represents the status of a pause target at a specific point in time.
   *
   * <p>This record captures the state of pods and deployment, including restart counts and resource
   * versions. It can be compared with another Status to detect changes during a pause operation.
   *
   * @param podRestartCounts a map of pod names to their restart counts
   * @param podResourceVersions a map of pod names to their resource versions
   * @param deploymentResourceVersion the resource version of the deployment
   */
  public record Status(
      Map<String, Integer> podRestartCounts,
      Map<String, String> podResourceVersions,
      String deploymentResourceVersion) {

    /**
     * Compact constructor with immutability enforcement.
     *
     * @param podRestartCounts a map of pod names to their restart counts
     * @param podResourceVersions a map of pod names to their resource versions
     * @param deploymentResourceVersion the resource version of the deployment
     */
    public Status {
      podRestartCounts = ImmutableMap.copyOf(podRestartCounts);
      podResourceVersions = ImmutableMap.copyOf(podResourceVersions);
    }
  }
}
