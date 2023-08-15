package com.scalar.admin.k8s;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

@Immutable
class TargetStatus {

  private final Map<String, Integer> podRestartCounts;
  private final Map<String, String> podResourceVersions;
  private final String deploymentResourceVersion;

  TargetStatus(
      Map<String, Integer> podRestartCounts,
      Map<String, String> podResourceVersions,
      String deploymentResourceVersion) {
    this.podRestartCounts = ImmutableMap.copyOf(podRestartCounts);
    this.podResourceVersions = ImmutableMap.copyOf(podResourceVersions);
    this.deploymentResourceVersion = deploymentResourceVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof TargetStatus)) {
      return false;
    }

    TargetStatus another = (TargetStatus) o;

    return (podRestartCounts.equals(another.podRestartCounts)
        && podResourceVersions.equals(another.podResourceVersions)
        && deploymentResourceVersion.equals(another.deploymentResourceVersion));
  }

  @Override
  public int hashCode() {
    return Objects.hash(podRestartCounts, podResourceVersions, deploymentResourceVersion);
  }
}
