package com.scalar.admin.k8s;

import java.util.Map;

class TargetStatus {

  private final Map<String, Integer> podRestartCounts;
  private final Map<String, String> podResourceVersions;

  private final String deploymentResourceVersion;

  TargetStatus(
      Map<String, Integer> podRestartCounts,
      Map<String, String> podResourceVersions,
      String deploymentResourceVersion) {
    this.podRestartCounts = podRestartCounts;
    this.podResourceVersions = podResourceVersions;
    this.deploymentResourceVersion = deploymentResourceVersion;
  }

  Boolean equals(TargetStatus another) {
    return (podRestartCounts.equals(another.podRestartCounts)
        && podResourceVersions.equals(another.podResourceVersions)
        && deploymentResourceVersion.equals(another.deploymentResourceVersion));
  }
}
