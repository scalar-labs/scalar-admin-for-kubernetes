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
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pauser {

  static final String DEFAULT_NAMESPACE = "default";

  private final Logger logger = LoggerFactory.getLogger(Pauser.class);
  private final TargetSelector targetSelector;

  /**
   * @param namespace The namespace where the pods are deployed. `default` is used if this parameter
   *     is null.
   * @param helmReleaseName The Helm release name used to deploy the pods. This parameter can not be
   *     null.
   */
  public Pauser(@Nullable String namespace, String helmReleaseName) {
    if (helmReleaseName == null) {
      throw new IllegalArgumentException("helmReleaseName is required");
    }

    namespace = namespace != null ? namespace : DEFAULT_NAMESPACE;

    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new RuntimeException("Failed to set default Kubernetes client. " + e.getMessage());
    }

    targetSelector =
        new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, helmReleaseName);
  }

  /**
   * @param pauseDuration The duration to pause in seconds.
   * @return 0 if the pause operation is successful. Otherwise, 1.
   */
  public int pause(Integer pauseDuration) {
    if (pauseDuration == null || pauseDuration < 1) {
      logger.error("pauseDuration is required and must be greater than 0 second.");
      return 1;
    }

    TargetSnapshot target;
    try {
      target = targetSelector.select();
    } catch (Exception e) {
      logger.error("Failed to find the target pods to pause. {}", e.getMessage());
      return 1;
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
      logger.error(
          "Failed to find the target pods to examine if the targets pods were updated during"
              + " paused. {}",
          e.getMessage());
      return 1;
    }

    if (!target.getStatus().equals(targetAfterPause.getStatus())) {
      logger.error("The target pods were updated during paused. Please retry.");
      return 1;
    }

    logger.info(
        "Paused successfully. Duration: from {} to {}.", startTime.toString(), endTime.toString());

    return 0;
  }
}
