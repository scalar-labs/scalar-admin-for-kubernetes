package com.scalar.admin.k8s;

import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pauser {

  private final Logger logger = LoggerFactory.getLogger(Pauser.class);
  private final TargetSelector targetSelector;

  /**
   * @param namespace The namespace where the pods are deployed.
   * @param helmReleaseName The Helm release name used to deploy the pods.
   */
  public Pauser(String namespace, String helmReleaseName) throws Exception {
    if (namespace == null) {
      throw new IllegalArgumentException("namespace is required");
    }

    if (helmReleaseName == null) {
      throw new IllegalArgumentException("helmReleaseName is required");
    }

    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new Exception("Failed to set default Kubernetes client.", e);
    }

    targetSelector =
        new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, helmReleaseName);
  }

  /**
   * @param pauseDuration The duration to pause in seconds.
   */
  public void pause(Integer pauseDuration) throws Exception {
    if (pauseDuration == null || pauseDuration < 1) {
      throw new IllegalArgumentException(
          "pauseDuration is required and must be greater than 0 second.");
    }

    TargetSnapshot target;
    try {
      target = targetSelector.select();
    } catch (Exception e) {
      throw new Exception("Failed to find the target pods to pause. {}", e);
    }

    RequestCoordinator coordinator =
        new RequestCoordinator(
            target.getPods().stream()
                .map(p -> new InetSocketAddress(p.getStatus().getPodIP(), target.getAdminPort()))
                .collect(Collectors.toList()));

    coordinator.pause(true, 0L);

    Instant startTime = Instant.now();

    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.SECONDS);

    Instant endTime = Instant.now();

    coordinator.unpause();

    TargetSnapshot targetAfterPause;
    try {
      targetAfterPause = targetSelector.select();
    } catch (Exception e) {
      throw new Exception(
          "Failed to find the target pods to examine if the targets pods were updated during"
              + " paused.",
          e);
    }

    if (!target.getStatus().equals(targetAfterPause.getStatus())) {
      throw new Exception("The target pods were updated during paused. Please retry.");
    }

    logger.info(
        "Paused successfully. Duration: from {} to {}.", startTime.toString(), endTime.toString());
  }
}
