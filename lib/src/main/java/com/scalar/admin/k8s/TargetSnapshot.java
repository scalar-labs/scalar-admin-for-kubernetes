package com.scalar.admin.k8s;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TargetSnapshot {

  private final List<V1Pod> pods;
  private final V1Deployment deployment;
  private final Integer adminPort;

  TargetSnapshot(List<V1Pod> pods, V1Deployment deployment, Integer adminPort) {
    this.pods = pods;
    this.deployment = deployment;
    this.adminPort = adminPort;
  }

  List<V1Pod> getPods() {
    return pods;
  }

  Integer getAdminPort() {
    return adminPort;
  }

  TargetStatus getStatus() {

    Map<String, Integer> podRestartCounts = new HashMap<String, Integer>();
    Map<String, String> podResourceVersions = new HashMap<String, String>();

    for (V1Pod pod : pods) {
      String podName = pod.getMetadata().getName();
      Integer restartCount =
          pod.getStatus().getContainerStatuses().stream().mapToInt(c -> c.getRestartCount()).sum();
      String resourceVersion = pod.getMetadata().getResourceVersion();

      podRestartCounts.put(podName, restartCount);
      podResourceVersions.put(podName, resourceVersion);
    }

    String deploymentResourceVersion = deployment.getMetadata().getResourceVersion();

    return new TargetStatus(podRestartCounts, podResourceVersions, deploymentResourceVersion);
  }
}
