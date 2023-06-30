package com.scalar.admin.k8s;

import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.models.V1Pod;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class InClusterPauser implements InternalPauser {
  public PausedDuration pause(List<V1Pod> pods, Integer adminPort, Integer pauseDuration) {
    RequestCoordinator coordinator =
        new RequestCoordinator(
            pods.stream()
                .map(p -> new InetSocketAddress(p.getStatus().getPodIP(), adminPort))
                .collect(Collectors.toList()));
    coordinator.pause(true, Long.valueOf(0));

    Date start = new Date();

    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.SECONDS);

    PausedDuration pausedDuration = new PausedDuration(start, new Date());

    coordinator.unpause();

    return pausedDuration;
  }
}
