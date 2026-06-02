package com.scalar.admin.kubernetes.domain.model.pause;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a pause target in the Kubernetes cluster.
 *
 * <p>This aggregate root encapsulates all information about the target of a pause operation,
 * including the pods, deployment, and admin port. It provides methods to extract status information
 * for comparison purposes.
 *
 * @param pods the list of pods that are part of this pause target
 * @param deployment the deployment associated with this pause target
 * @param adminPort the admin port number used for pause operations
 */
public record PauseTarget(List<V1Pod> pods, V1Deployment deployment, int adminPort) {

  private static final Logger logger = LoggerFactory.getLogger(PauseTarget.class);

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
      if (pod.getMetadata() == null || pod.getMetadata().getName() == null) {
        logger.warn("Skipping pod with null metadata or name during status check.");
        continue;
      }
      String podName = pod.getMetadata().getName();
      int restartCount = 0;
      if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
        restartCount =
            pod.getStatus().getContainerStatuses().stream()
                .mapToInt(c -> c.getRestartCount() != null ? c.getRestartCount() : 0)
                .sum();
      } else {
        logger.info(
            "Pod {} has no status or container statuses; treating restart count as 0.", podName);
      }
      String resourceVersion = pod.getMetadata().getResourceVersion();

      podRestartCounts.put(podName, restartCount);
      podResourceVersions.put(podName, resourceVersion);
    }

    String deploymentResourceVersion =
        deployment.getMetadata() != null ? deployment.getMetadata().getResourceVersion() : null;
    if (deploymentResourceVersion == null) {
      logger.warn("Deployment has null metadata; resource version will be null.");
    }

    return new Status(podRestartCounts, podResourceVersions, deploymentResourceVersion);
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
