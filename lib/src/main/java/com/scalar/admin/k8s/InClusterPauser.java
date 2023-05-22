package com.scalar.admin.k8s;

import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.admin.RequestCoordinator;
import io.kubernetes.client.openapi.models.V1Pod;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class InClusterPauser implements InternalPauser {
  public void pause(List<V1Pod> pods, Integer adminPort, Integer pauseDuration) {
    RequestCoordinator coordinator =
        new RequestCoordinator(
            pods.stream()
                .map(p -> new InetSocketAddress(p.getStatus().getPodIP(), adminPort))
                .collect(Collectors.toList()));
    coordinator.pause(true, Long.valueOf(0));
    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.SECONDS);
    coordinator.unpause();
  }
}
